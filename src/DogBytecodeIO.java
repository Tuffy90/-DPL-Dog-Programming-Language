import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Binary serializer for DPL bytecode files (.dogc).
 *
 * source (.dog) -> compile -> save (.dogc)
 * then run (.dogc) without parsing/compiling text again.
 */
public final class DogBytecodeIO {

    // 'D' 'P' 'L' 'C'
    private static final int MAGIC = 0x44504C43;
    private static final int VERSION = 2; // ✅ bumped because format changed

    private DogBytecodeIO() {
    }

    public static void writeChunk(Chunk chunk, Path file) throws IOException {
        if (chunk == null)
            throw new IllegalArgumentException("chunk is null");
        if (file == null)
            throw new IllegalArgumentException("file is null");

        Path parent = file.toAbsolutePath().normalize().getParent();
        if (parent != null)
            Files.createDirectories(parent);

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {

            out.writeInt(MAGIC);
            out.writeInt(VERSION);

            out.writeInt(chunk.code().size());
            for (Instruction ins : chunk.code()) {
                writeInstruction(out, ins);
            }
        }
    }

    public static Chunk readChunk(Path file) throws IOException {
        if (file == null)
            throw new IllegalArgumentException("file is null");

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {

            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IOException("Not a .dogc file (bad magic)");
            }

            int ver = in.readInt();
            if (ver != VERSION) {
                throw new IOException("Unsupported .dogc version: " + ver + " (expected " + VERSION + ")");
            }

            int count = in.readInt();
            if (count < 0 || count > 10_000_000) {
                throw new IOException("Corrupted .dogc (bad instruction count): " + count);
            }

            Chunk chunk = new Chunk();
            for (int i = 0; i < count; i++) {
                chunk.add(readInstruction(in));
            }
            return chunk;

        } catch (EOFException eof) {
            throw new IOException("Corrupted .dogc (unexpected end of file)", eof);
        }
    }

    // -------------------------
    // Instruction I/O
    // -------------------------
    private static void writeInstruction(DataOutputStream out, Instruction ins) throws IOException {
        out.writeInt(ins.op.ordinal());
        out.writeInt(ins.line);
        out.writeInt(ins.col);
        writeString(out, ins.sourceLine);

        switch (ins.op) {
            case CONST_NUM:
                out.writeDouble(ins.num != null ? ins.num.doubleValue() : 0.0);
                break;

            case CONST_STR:
                writeString(out, ins.text);
                break;

            case CONST_BOOL:
                out.writeBoolean(ins.boolVal != null && ins.boolVal.booleanValue());
                break;

            case CONST_NIL:
                // no payload
                break;

            case LOAD:
            case STORE:
                writeString(out, ins.name);
                break;

            case IMPORT:
                writeString(out, ins.module);
                break;

            case CALL:
                writeString(out, ins.module);
                writeString(out, ins.member);
                out.writeInt(ins.argCount);
                out.writeBoolean(ins.isConst);
                break;

            case JUMP:
            case JUMP_IF_FALSE:
                out.writeInt(ins.jumpTarget);
                break;

            default:
                // simple opcode: no extra fields
                break;
        }
    }

    private static Instruction readInstruction(DataInputStream in) throws IOException {
        int opOrdinal = in.readInt();
        if (opOrdinal < 0 || opOrdinal >= OpCode.values().length) {
            throw new IOException("Corrupted .dogc (bad opcode): " + opOrdinal);
        }
        OpCode op = OpCode.values()[opOrdinal];

        int line = in.readInt();
        int col = in.readInt();
        String src = readString(in);

        switch (op) {
            case CONST_NUM: {
                double n = in.readDouble();
                return Instruction.constNum(n, line, col, src);
            }

            case CONST_STR: {
                String s = readString(in);
                return Instruction.constStr(s, line, col, src);
            }

            case CONST_BOOL: {
                boolean b = in.readBoolean();
                return Instruction.constBool(b, line, col, src);
            }

            case CONST_NIL: {
                return Instruction.constNil(line, col, src);
            }

            case LOAD: {
                String var = readString(in);
                return Instruction.load(var, line, col, src);
            }

            case STORE: {
                String var = readString(in);
                return Instruction.store(var, line, col, src);
            }

            case IMPORT: {
                String mod = readString(in);
                return Instruction.importMod(mod, line, col, src);
            }

            case CALL: {
                String mod = readString(in);
                String member = readString(in);
                int argc = in.readInt();
                boolean isConst = in.readBoolean();
                return Instruction.call(mod, member, argc, isConst, line, col, src);
            }

            case JUMP: {
                int target = in.readInt();
                return Instruction.jump(target, line, col, src);
            }

            case JUMP_IF_FALSE: {
                int target = in.readInt();
                return Instruction.jumpIfFalse(target, line, col, src);
            }

            default:
                return Instruction.simple(op, line, col, src);
        }
    }

    // -------------------------
    // Safe UTF-8 strings (no 64K limit like writeUTF)
    // -------------------------
    private static void writeString(DataOutputStream out, String s) throws IOException {
        if (s == null) {
            out.writeInt(-1);
            return;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len == -1)
            return null;
        if (len < 0 || len > 100_000_000) {
            throw new IOException("Corrupted .dogc (bad string length): " + len);
        }
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
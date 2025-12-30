
// File: DogBytecodeIO.java
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * DogBytecodeIO v3
 *
 * Формат (v3):
 * - magic: 4 bytes "DOGC"
 * - version: int (3)
 * - root chunk: writeChunk()
 *
 * writeChunk():
 * - functionsCount: int
 * - for each function:
 * - paramsCount: int
 * - params: UTF strings
 * - bodyChunk: writeChunk() (рекурсивно)
 * - codeCount: int
 * - for each instruction:
 * - opcodeOrdinal: int (OpCode.ordinal())
 * - payload (зависит от opcode)
 * - debug:
 * - line: int
 * - col: int
 * - hasSourceLine: boolean
 * - sourceLine: UTF string (если hasSourceLine)
 */
public final class DogBytecodeIO {

    private static final byte[] MAGIC = new byte[] { 'D', 'O', 'G', 'C' };
    private static final int VERSION = 3;

    private DogBytecodeIO() {
    }

    // -----------------------
    // Public API
    // -----------------------

    public static void writeToFile(Chunk chunk, Path file) throws IOException {
        if (chunk == null)
            throw new IllegalArgumentException("chunk is null");
        if (file == null)
            throw new IllegalArgumentException("file is null");

        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(file))) {
            writeToStream(chunk, os);
        }
    }

    public static Chunk readFromFile(Path file) throws IOException {
        if (file == null)
            throw new IllegalArgumentException("file is null");

        try (InputStream is = new BufferedInputStream(Files.newInputStream(file))) {
            return readFromStream(is);
        }
    }

    public static void writeToStream(Chunk chunk, OutputStream os) throws IOException {
        if (chunk == null)
            throw new IllegalArgumentException("chunk is null");
        if (os == null)
            throw new IllegalArgumentException("os is null");

        DataOutputStream out = new DataOutputStream(os);

        // header
        out.write(MAGIC);
        out.writeInt(VERSION);

        // body
        writeChunk(out, chunk);

        out.flush();
    }

    public static Chunk readFromStream(InputStream is) throws IOException {
        if (is == null)
            throw new IllegalArgumentException("is is null");

        DataInputStream in = new DataInputStream(is);

        // header
        byte[] mg = new byte[4];
        try {
            in.readFully(mg);
        } catch (EOFException e) {
            throw new IOException("Not a DOGC file (EOF before magic)");
        }

        if (!eq4(mg, MAGIC)) {
            throw new IOException("Not a DOGC file (bad magic)");
        }

        int ver = in.readInt();
        if (ver != VERSION) {
            throw new IOException("Unsupported DOGC version: " + ver + " (expected " + VERSION + ")");
        }

        return readChunk(in);
    }

    // -----------------------
    // Chunk IO (recursive)
    // -----------------------

    private static void writeChunk(DataOutputStream out, Chunk chunk) throws IOException {
        // functions table
        int fCount = chunk.functions().size();
        out.writeInt(fCount);
        for (int i = 0; i < fCount; i++) {
            FunctionProto fp = chunk.getFunction(i);

            // params
            out.writeInt(fp.params.size());
            for (int p = 0; p < fp.params.size(); p++) {
                writeUtf(out, fp.params.get(p));
            }

            // body chunk (recursive)
            writeChunk(out, fp.body);
        }

        // code
        int cCount = chunk.code().size();
        out.writeInt(cCount);

        for (int i = 0; i < cCount; i++) {
            Instruction ins = chunk.code().get(i);

            // opcode
            out.writeInt(ins.op.ordinal());

            // payload
            writePayload(out, ins);

            // debug
            out.writeInt(ins.line);
            out.writeInt(ins.col);

            if (ins.sourceLine != null) {
                out.writeBoolean(true);
                writeUtf(out, ins.sourceLine);
            } else {
                out.writeBoolean(false);
            }
        }
    }

    private static Chunk readChunk(DataInputStream in) throws IOException {
        Chunk chunk = new Chunk();

        // functions table
        int fCount = in.readInt();
        if (fCount < 0)
            throw new IOException("Corrupt DOGC: negative functionsCount");

        for (int i = 0; i < fCount; i++) {
            int pCount = in.readInt();
            if (pCount < 0)
                throw new IOException("Corrupt DOGC: negative paramsCount");

            java.util.ArrayList<String> params = new java.util.ArrayList<>();
            for (int p = 0; p < pCount; p++) {
                params.add(readUtf(in));
            }

            Chunk body = readChunk(in); // recursive
            chunk.addFunction(new FunctionProto(params, body));
        }

        // code
        int cCount = in.readInt();
        if (cCount < 0)
            throw new IOException("Corrupt DOGC: negative codeCount");

        for (int i = 0; i < cCount; i++) {
            int opOrdinal = in.readInt();
            OpCode op = opcodeByOrdinal(opOrdinal);

            // payload (must be read before debug)
            Payload payload = readPayload(in, op);

            // debug
            int line = in.readInt();
            int col = in.readInt();
            boolean hasSrc = in.readBoolean();
            String srcLine = hasSrc ? readUtf(in) : null;

            Instruction ins = buildInstruction(op, payload, line, col, srcLine);
            chunk.add(ins);
        }

        return chunk;
    }

    // -----------------------
    // Payload encoding
    // -----------------------

    private static void writePayload(DataOutputStream out, Instruction ins) throws IOException {
        switch (ins.op) {
            // consts
            case CONST_INT:
                out.writeInt(ins.intVal != null ? ins.intVal : 0);
                break;
            case CONST_LONG:
                out.writeLong(ins.longVal != null ? ins.longVal : 0L);
                break;
            case CONST_DOUBLE:
                out.writeDouble(ins.doubleVal != null ? ins.doubleVal : 0.0);
                break;
            case CONST_BIGINT:
                writeUtf(out, ins.text != null ? ins.text : "0");
                break;
            case CONST_STR:
                writeUtf(out, ins.text != null ? ins.text : "");
                break;
            case CONST_BOOL:
                out.writeBoolean(ins.boolVal != null && ins.boolVal);
                break;
            case CONST_NIL:
                // no payload
                break;

            // functions
            case CONST_FUNC:
                out.writeInt(ins.funcIndex);
                break;
            case CALL_VALUE:
                out.writeInt(ins.argCount);
                break;
            case RETURN:
                // no payload
                break;

            // arrays
            case ARRAY_NEW:
                out.writeInt(ins.argCount);
                break;
            case ARRAY_GET:
            case ARRAY_SET:
                // no payload
                break;

            // vars
            case LOAD:
            case STORE:
                writeUtf(out, ins.name != null ? ins.name : "");
                break;

            // modules
            case IMPORT:
                writeUtf(out, ins.module != null ? ins.module : "");
                break;

            case CALL:
                writeUtf(out, ins.module != null ? ins.module : "");
                writeUtf(out, ins.member != null ? ins.member : "");
                out.writeInt(ins.argCount);
                out.writeBoolean(ins.isConst);
                break;

            // jumps
            case JUMP:
            case JUMP_IF_FALSE:
                out.writeInt(ins.jumpTarget);
                break;

            // simple ops (no payload)
            default:
                break;
        }
    }

    private static Payload readPayload(DataInputStream in, OpCode op) throws IOException {
        Payload p = new Payload();

        switch (op) {
            // consts
            case CONST_INT:
                p.i = in.readInt();
                break;
            case CONST_LONG:
                p.l = in.readLong();
                break;
            case CONST_DOUBLE:
                p.d = in.readDouble();
                break;
            case CONST_BIGINT:
                p.s1 = readUtf(in);
                break;
            case CONST_STR:
                p.s1 = readUtf(in);
                break;
            case CONST_BOOL:
                p.b = in.readBoolean();
                break;
            case CONST_NIL:
                break;

            // functions
            case CONST_FUNC:
                p.i = in.readInt(); // funcIndex
                break;
            case CALL_VALUE:
                p.i = in.readInt(); // argCount
                break;
            case RETURN:
                break;

            // arrays
            case ARRAY_NEW:
                p.i = in.readInt(); // count
                break;
            case ARRAY_GET:
            case ARRAY_SET:
                break;

            // vars
            case LOAD:
            case STORE:
                p.s1 = readUtf(in); // name
                break;

            // modules
            case IMPORT:
                p.s1 = readUtf(in); // module
                break;

            case CALL:
                p.s1 = readUtf(in); // module
                p.s2 = readUtf(in); // member
                p.i = in.readInt(); // argCount
                p.b = in.readBoolean(); // isConst
                break;

            // jumps
            case JUMP:
            case JUMP_IF_FALSE:
                p.i = in.readInt(); // target
                break;

            // simple ops
            default:
                break;
        }

        return p;
    }

    private static Instruction buildInstruction(OpCode op, Payload p, int line, int col, String srcLine) {
        switch (op) {
            // consts
            case CONST_INT:
                return Instruction.constInt(p.i, line, col, srcLine);
            case CONST_LONG:
                return Instruction.constLong(p.l, line, col, srcLine);
            case CONST_DOUBLE:
                return Instruction.constDouble(p.d, line, col, srcLine);
            case CONST_BIGINT:
                return Instruction.constBigInt(p.s1, line, col, srcLine);
            case CONST_STR:
                return Instruction.constStr(p.s1, line, col, srcLine);
            case CONST_BOOL:
                return Instruction.constBool(p.b, line, col, srcLine);
            case CONST_NIL:
                return Instruction.constNil(line, col, srcLine);

            // functions
            case CONST_FUNC:
                return Instruction.constFunc(p.i, line, col, srcLine);
            case CALL_VALUE:
                return Instruction.callValue(p.i, line, col, srcLine);
            case RETURN:
                return Instruction.ret(line, col, srcLine);

            // arrays
            case ARRAY_NEW:
                return Instruction.arrayNew(p.i, line, col, srcLine);
            case ARRAY_GET:
                return Instruction.arrayGet(line, col, srcLine);
            case ARRAY_SET:
                return Instruction.arraySet(line, col, srcLine);

            // vars
            case LOAD:
                return Instruction.load(p.s1, line, col, srcLine);
            case STORE:
                return Instruction.store(p.s1, line, col, srcLine);

            // modules
            case IMPORT:
                return Instruction.importMod(p.s1, line, col, srcLine);
            case CALL:
                return Instruction.call(p.s1, p.s2, p.i, p.b, line, col, srcLine);

            // jumps
            case JUMP:
                return Instruction.jump(p.i, line, col, srcLine);
            case JUMP_IF_FALSE:
                return Instruction.jumpIfFalse(p.i, line, col, srcLine);

            // simple
            default:
                return Instruction.simple(op, line, col, srcLine);
        }
    }

    // -----------------------
    // Helpers
    // -----------------------

    private static OpCode opcodeByOrdinal(int ord) throws IOException {
        OpCode[] all = OpCode.values();
        if (ord < 0 || ord >= all.length) {
            throw new IOException("Corrupt DOGC: bad opcode ordinal: " + ord);
        }
        return all[ord];
    }

    private static boolean eq4(byte[] a, byte[] b) {
        if (a == b)
            return true;
        if (a == null || b == null)
            return false;
        if (a.length != 4 || b.length != 4)
            return false;
        return a[0] == b[0] && a[1] == b[1] && a[2] == b[2] && a[3] == b[3];
    }

    private static void writeUtf(DataOutputStream out, String s) throws IOException {
        if (s == null)
            s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readUtf(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0)
            throw new IOException("Corrupt DOGC: negative string length");
        if (n == 0)
            return "";
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static final class Payload {
        int i;
        long l;
        double d;
        boolean b;
        String s1;
        String s2;
    }

    public static void writeChunk(Chunk chunk, Path out) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(out)))) {
            writeChunk(dos, chunk); // <-- вызывает твой текущий метод
            dos.flush();
        }
    }

    public static Chunk readChunk(Path in) throws IOException {
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(in)))) {
            return readChunk(dis); // <-- вызывает твой текущий метод
        }
    }
}
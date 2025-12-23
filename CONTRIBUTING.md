Thanks for contributing to **Dog Programming Language (DPL)**! This is a learning project built around a **Compiler → Bytecode → VM** pipeline. These guidelines keep the repo clean and stable.

## 1) Authorship, forks, and attribution

- **Do not claim you wrote the original project.** Forks and modifications are allowed, but **original authorship must remain**.
- Do not use **Tuffy90 / Tuffy Rej / DPL / Dog Programming Language** to endorse/promote derived products without written permission.
- Want to contribute a major feature (new control flow, scopes, big refactor)? **Discuss it with the author first** via an Issue/Discussion.

> This matches the repo license (Modified MIT: Attribution & No Misrepresentation).

## 2) What to contribute

### Great PRs
- bug fixes (parser/compiler/vm/console)
- better diagnostics (line/column, clearer messages)
- small syntax improvements (without breaking existing code)
- stdlib modules (`io`, `math`, new modules)
- docs (README, examples)

### Please discuss first
- `.dogc` format / bytecode serialization changes
- new keywords/control flow (`while`, `fn`, `return`, scopes)
- operator precedence / parsing rule changes

## 3) Dev setup

### Requirements
- **Java JDK 8+** (17+ recommended)
- Git

> Building a JAR requires **javac** and **jar** (JDK, not just JRE).

### Project scripts
Usually in `scripts/`:

- `build_jar_windows.bat` — build `dist/dpl.jar` (Windows)
- `run_windows.bat` — run `dist/dpl.jar` (Windows)
- `build_jar_unix.sh` — build JAR (Linux/macOS)
- `run_unix.sh` — run (Linux/macOS)

### Manual build (no scripts)
```bash
javac -encoding UTF-8 -d out *.java
java -cp out Code
```

## 4) Rules for changes

### If you touch bytecode/VM
- Any new instruction must update:
- `OpCode`
- `Instruction` (payload, if needed)
- `DogVM` (execution)
- `BytecodeCompiler` (emission)
- `DogBytecodeIO` (read/write `.dogc` if payload changes)
- If `.dogc` format changes: **bump `VERSION`** in `DogBytecodeIO` and document the change.

### If you add syntax
- Avoid breaking existing `.dog` files.
- Add at least **one example program** under `test_files_RDL/`.

### If you add a stdlib module
- Register it in the context creation / `ModuleRegistry`.
- Add 1–3 usage examples under `test_files_RDL/`.

## 5) Code style

- Language: **Java**
- Keep it simple and readable
- Prefer `DogException.at(line, col, sourceLine, message)` for errors
- Avoid large dependencies

## 6) Filing issues

### Bug report
Include:
- OS, Java version (`java -version`)
- how you run it (REPL or `java -jar ...`)
- minimal `.dog` file reproducer
- actual output/error text

### Feature request
Include:
- what you want
- proposed `.dog` syntax
- expected behavior + edge cases

## 7) PR checklist

Before you open a PR:
- [ ] It builds on your platform
- [ ] You added an example under `test_files_RDL/` (if behavior changed)
- [ ] Diagnostics are clear (line/col)
- [ ] You follow the repo license and attribution rules
- [ ] Small, focused PR

## 8) License and contributions

By submitting code, you confirm:
- you have the right to submit it
- your contribution is licensed under the repo license
- you are not misrepresenting authorship of the original project


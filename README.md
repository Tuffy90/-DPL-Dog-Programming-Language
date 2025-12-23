# Dog Programming Language (DPL) 🐶
*A tiny, fun programming language written in Java — built for learning, experiments, and vibes.*

> **Note:** Dog (DPL) is created for entertainment and educational purposes. You can use it, study it, fork it, and build on top of it — just don’t misrepresent authorship.

---

## Table of Contents
- [What is DPL?](#what-is-dpl)
- [Goals](#goals)
- [Features](#features)
- [Quick Start](#quick-start)
- [Build & Run Scripts](#build--run-scripts)
- [Project Structure](#project-structure)
- [Language Syntax](#language-syntax)
- [REPL Console](#repl-console)
- [CLI Mode (Run / Compile)](#cli-mode-run--compile)
- [Example Programs](#example-programs)
- [Errors](#errors)
- [How it Works (Bytecode + VM + .dogc)](#how-it-works-bytecode--vm--dogc)
- [Roadmap](#roadmap)
- [License](#license)
- [Author](#author)

---

## What is DPL?
**Dog Programming Language (DPL)** is a small custom language that runs anywhere **Java** runs.
It’s designed to be:
- easy to read
- fun to extend
- great for learning how interpreters/compilers work
- modular (libraries/modules are separate Java classes)

DPL uses a **Compiler → Bytecode → VM** pipeline and also supports saving/loading compiled bytecode as **`.dogc`**.

---

## Goals
- ✅ Run on **Windows / Linux / macOS** via Java
- ✅ Keep syntax simple but expandable
- ✅ Modular standard library (`io`, `math`, etc.)
- ✅ Readable errors (line/column + pointer)
- ✅ REPL console (interactive)
- ✅ Bytecode + Virtual Machine architecture
- ✅ **Compile once → run fast** with `.dogc`

---

## Features

### Core Language
- Output: `say <expr>`
- Imports: `import <module>`
- Variables:
- declaration: `let x = ...`
- assignment: `x = ...`
- Types:
- `NUMBER`, `STRING`, `BOOL`, `NIL`
- literals: `true`, `false`, `nil`
- Expressions:
- numbers: `1`, `3.14`
- strings: `"hello"`
- arithmetic: `+ - * /`
- parentheses: `(2 + 3) * 4`
- string concatenation with `+`
- Comparisons:
- `==`, `!=`, `<>`, `<`, `>`, `<=`, `>=`
- Unary:
- `!` (NOT)
- Conditionals:
- `if <expr> { ... } else { ... }`

### Standard Library (stdlib)
- `io.print(value)`
- `io.println(value)`
- `math.sqrt(x)`
- `math.pow(a, b)`
- constants:
- `math.PI`
- `math.E`

### Tooling
- REPL console with basic file manager:
- create folder/file
- open/edit file
- run `.dog` scripts
- compile `.dog` → `.dogc`
- run `.dogc`

---

## Quick Start

### Requirements
- **Java 8+** (recommended 17+)
- For building JAR: **JDK required** (needs `javac` + `jar`)

---

## Build & Run Scripts

This repo includes cross-platform scripts (Windows + Unix/macOS).

### Windows (BAT)
- `build_jar_windows.bat`
Builds `dist/dpl.jar` and prepares runtime folders.
- `run_windows.bat`
Runs the REPL if no args are given, or runs a file if you pass a path.
- `uninstall_windows.bat`
Cleans generated folders (like `dist/`, `out/`) — safe cleanup.

### Unix / macOS (SH)
- `build_jar_unix.sh`
Builds `dist/dpl.jar`.
- `run_unix.sh`
Runs DPL (REPL or file), same idea as Windows script.

> Tip: on Unix/macOS run `chmod +x scripts/*.sh` once.

---

## Project Structure

Recommended structure:
```
DPL/
src/ # Java sources
dist/ # built jar output (dpl.jar)
out/ # compiled bytecode output (.dogc) (optional)
test_files_RDL/ # demo scripts
scripts/ # .bat / .sh scripts
assets/ # icons etc. (optional)
```

---

## Language Syntax

### Comments
Single-line comments start with `#`:
```dog
# This is a comment
say "Hello"
```

### Say (print)
`say` prints the result of an expression:
```dog
say "Hello world"
say 2 + 3 * 4
```

### Imports
Modules must be imported before use:
```dog
import io
import math
```

### Variables
Declare:
```dog
let x = 10
```

Assign:
```dog
x = x + 5
```

Use:
```dog
say x
```

### Booleans & Nil
```dog
let ok = true
let no = false
let empty = nil

say ok
say !ok
say empty
```

### Comparisons
```dog
let a = 10
let b = 3

say a > b
say a < b
say a == b
say a != b
say a <> b
say a >= 10
say b <= 3
```

### If / Else
```dog
import io

let a = 10
let b = 3

if a > b {
io.println("a is greater than b")
} else {
io.println("a is NOT greater than b")
}
```

---

## REPL Console

Start REPL:
```bash
java -jar dist/dpl.jar
```

Common console commands (may vary depending on your console implementation):
- `:help` — show commands
- `:exit` — exit
- `:pwd` — current directory
- `:ls` — list files/folders
- `:cd <folder|..>` — change directory
- `:mkdir <folder>` — create folder
- `:touch <file>` — create empty file
- `:open <file>` — show file content
- `:edit <file>` — edit file (`:wq` save & quit, `:q` quit without save)
- `:run <file.dog>` — compile + run source
- `:compile <file.dog> <out.dogc>` — compile to bytecode
- `:vars` — show variables in current session

---

## CLI Mode (Run / Compile)

If your `Code.java` supports arguments:

### Start console (REPL)
```bash
java -jar dist/dpl.jar
```

### Run `.dog` source
```bash
java -jar dist/dpl.jar program.dog
```

### Run compiled `.dogc`
```bash
java -jar dist/dpl.jar program.dogc
```

### Compile only (`.dog` → `.dogc`)
```bash
java -jar dist/dpl.jar -c program.dog
java -jar dist/dpl.jar -c program.dog out/program.dogc
```

---

## Example Programs

### 1) Hello Dog
```dog
say "Hello from Dog!"
```

### 2) Variables + Math
```dog
import math
let x = 9
say "sqrt(9) = " + math.sqrt(x)
say "PI = " + math.PI
```

### 3) If / Else demo
```dog
import io

let a = 10
let b = 3

if a > b {
io.println("THEN")
} else {
io.println("ELSE")
}

io.println("END")
```

---

## Errors
DPL errors show **line + column** and a pointer:
```
Dog error at line 3, column 7: Undefined variable 'x'
say x
^
```

---

## How it Works (Bytecode + VM + .dogc)

### 1) Compiler (`BytecodeCompiler`)
- reads `.dog` source lines
- parses statements + expressions
- emits instructions into a `Chunk`

### 2) Bytecode (`Chunk`, `Instruction`, `OpCode`)
Instruction examples:
- push constants: numbers/strings/bools/nil
- arithmetic: add/sub/mul/div
- comparisons + logic: `== != < > <= >= !`
- variables: load/store
- modules: import/call
- flow: jump / jump-if-false

### 3) Virtual Machine (`DogVM`)
- executes instructions
- uses a stack for expression evaluation
- stores globals in a map
- calls modules via `DogContext` and `ModuleRegistry`

### 4) `.dogc` (compiled bytecode file)
- compile once and save
- run later without parsing source text again

---

## Roadmap
Planned upgrades:
- [ ] `while` loops
- [ ] functions: `fn name(args) { ... }`
- [ ] local scopes (not only globals)
- [ ] arrays / maps (future)
- [ ] more stdlib modules: `string`, `time`, `file`, `random`
- [ ] Windows file icon association for `.dog` (optional installer step)

---

## License
MIT License (Modified - Attribution & No Misrepresentation)

---

## Author
**Tuffy Rej**
Dog Programming Language (DPL) — built in Java


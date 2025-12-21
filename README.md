# Dog Programming Language (DPL) 🐶
*A tiny, fun programming language written in Java — built for learning, experiments, and vibes.*

> **Note:** Dog (DPL) is created for entertainment and educational purposes. Anyone can use it, study it, fork it, and build on top of it.

---

## Table of Contents
- [What is DPL?](#what-is-dpl)
- [Goals](#goals)
- [Features](#features)
- [Quick Start](#quick-start)
- [Project Structure](#project-structure)
- [Language Syntax](#language-syntax)
- [Comments](#comments)
- [Say (print)](#say-print)
- [Imports](#imports)
- [Standard Library](#standard-library)
- [Variables](#variables)
- [Expressions](#expressions)
- [REPL Console](#repl-console)
- [Example Programs](#example-programs)
- [Errors](#errors)
- [How it Works (Bytecode + VM)](#how-it-works-bytecode--vm)
- [Roadmap](#roadmap)
- [License](#license)
- [Author](#author)

---

## What is DPL?
**Dog Programming Language (DPL)** is a small custom language that runs on **any platform with Java**.
It is designed to be:
- easy to read
- fun to extend
- great for learning how interpreters/compilers work
- modular (libraries/modules are separate classes)

DPL is evolving from a basic interpreter into a **Bytecode + Virtual Machine** architecture.

---

## Goals
- ✅ Run on **Windows / Linux / macOS** (and later Android) via Java
- ✅ Keep syntax simple but expandable
- ✅ Have a modular standard library (`io`, `math`, etc.)
- ✅ Provide readable errors (line/column)
- ✅ Support REPL (interactive console)
- ✅ Use a proper execution pipeline: **Compiler → Bytecode → VM**

---

## Features
### Core Language
- `say <expr>` output
- `import <module>` system
- variables:
- `let x = ...`
- `x = ...`
- use `x` inside expressions
- expressions:
- numbers: `1`, `3.14`
- strings: `"hello"`
- math: `+ - * /`
- parentheses: `(2 + 3) * 4`
- string concatenation with `+`

### Standard Library (stdlib)
- `io.print(value)`
- `io.println(value)`
- `math.sqrt(x)`
- `math.pow(a, b)`
- `math.abs(x)`
- `math.floor(x)`
- `math.ceil(x)`
- `math.round(x)`
- constants:
- `math.PI`
- `math.E`

### Tooling
- REPL console with basic file manager:
- create folder/file
- open/edit file
- run `.dog` scripts

---

## Quick Start

### Requirements
- **Java JDK 8+** (recommended 17+)

### Compile
In the project folder:
```bash
javac *.java
```

### Run REPL Console
```bash
java Code
```

### Run a `.dog` file
```bash
java Code test.dog
```

---

## Project Structure
Typical structure (you can keep all `.java` files in one folder for now):

```
/Dog-code
Code.java
DogConsole.java
DogVM.java
BytecodeCompiler.java
Chunk.java
Instruction.java
OpCode.java

Value.java
DogException.java
DogModule.java
ModuleRegistry.java
DogContext.java
IoModule.java
MathModule.java
```

---

## Language Syntax

### Comments
Single-line comments start with `#`:
```dog
# This is a comment
say "Hello"
```

---

### Say (print)
`say` prints the result of an expression:
```dog
say "Hello world"
say 2 + 3 * 4
```

---

### Imports
Modules must be imported before use:
```dog
import io
import math
```

---

### Standard Library

#### io
```dog
import io
io.print("HP: ")
io.println(100)
```

#### math
```dog
import math
say math.sqrt(9)
say math.pow(2, 8)
say math.PI
```

---

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

---

### Expressions
Supported:
- numbers: `10`, `2.5`
- strings: `"text"`
- arithmetic: `+ - * /`
- parentheses: `( )`
- string concat:
```dog
say "Hello " + "Dog"
say "x = " + 10
```

---

## REPL Console
Start:
```bash
java Code
```

### Console Commands
- `:help` — show commands
- `:exit` — exit
- `:pwd` — current directory
- `:ls` — list files/folders
- `:cd <folder|..>` — change directory
- `:mkdir <folder>` — create folder
- `:touch <file>` — create empty file
- `:open <file>` — show file content
- `:edit <file>` — edit file (`:wq` save & quit, `:q` quit without save)
- `:run <file.dog>` — run a script in current session (keeps variables/imports)
- `:save <file.dog>` — save session history to file
- `:vars` — show variables stored in current session
- `:clear` — clear session history

---

## Example Programs

### 1) Hello Dog
```dog
say "Hello from Dog!"
```

### 2) Variables
```dog
let x = 10
x = x + 5
say "x = " + x
```

### 3) IO + Math
```dog
import io
import math

let a = 9
io.println("sqrt(9) = " + math.sqrt(a))
io.println("PI = " + math.PI)
```

---

## Errors
DPL errors show **line + column** and a pointer:
```
Dog error at line 3, column 7: Undefined variable 'x'
say x
^
```

This helps you locate mistakes quickly.

---

## How it Works (Bytecode + VM)

DPL uses a simple execution pipeline:

1) **Compiler**
- reads `.dog` source code
- parses it (expressions, statements, calls)
- outputs bytecode instructions into a `Chunk`

2) **Bytecode**
A list of compact instructions, like:
- push number / push string
- add/mul/div
- load/store variables
- import module
- call module functions/constants
- print / pop

3) **Virtual Machine (VM)**
- executes instructions
- keeps a stack for calculations
- stores variables in a global map
- resolves module calls through `DogContext` and `ModuleRegistry`

This design is scalable and lets the language grow without turning the interpreter into a “giant if-else monster”.

---

## Roadmap
Planned upgrades:
- [ ] `io.input()` (user input)
- [ ] `if / else`
- [ ] `while` loops
- [ ] functions: `fn name(args) { ... }`
- [ ] local scopes (not only global variables)
- [ ] save/load compiled bytecode: `.dogc`
- [ ] more stdlib modules:
- `string`
- `time`
- `file`
- `random`
- [ ] multimedia modules (future):
- `audio`
- `graphics`
- `input`

---

## License
This project uses the **MIT License**.
You are free to use, copy, modify, merge, publish, distribute, and sublicense the code.

---

## Author
**Tuffy Rej**
Dog Programming Language (DPL) — built in Java and shared publicly on GitHub.


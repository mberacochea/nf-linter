
# Nextflow Linter

A command-line tool for linting **Nextflow scripts** to detect syntax and semantic issues. Powered by the excellent **Nextflow Language Server tools**.

> **IMPORTANT:**  
> I wrote this mostly for fun, to learn a bit of Groovy, but it could be useful. The Seqera folks will add a linter of sorts into Nextflow itself at some point, so I won't maintain this tool in the long run.

---

## Features

This tool should print exactly the same errors and warnings as the Nextflow VS Code extension.

It supports individual `.nf` or `.config` files or to recursively lint all `.nf` and `.config` files in a directory.

The linter will exit with code `1` if there is at least one error.

---

## Installation

### Requirements
- Java 11 or later
- Groovy
- Gradle (or use the wrapper provided in the repository)

### Build the Tool
Clone the repository and build the project:

```bash
$ git clone https://github.com/mberacochea/nf-linter.git
$ cd nf-linter
$ ./gradlew build
```

The generated JAR file will be available in the `build/libs` directory:

```plaintext
build/libs/nf-linter.jar
```

---

## Usage

### Command Syntax
```bash
java -jar nf-linter.jar <script_or_directory_path>
```

### Examples

#### Lint a Single File
```bash
java -jar nf-linter.jar /path/to/script.nf
```

#### Lint All `.nf` and `.config` files in a directory
```bash
java -jar nf-linter.jar /path/to/scripts/
```

#### Display Help
```bash
java -jar nf-linter.jar --help
```

### Output Example

```bash
➜  nf-linter git:(main) java -jar build/libs/nf-linter.jar src/test/test.nf 
---------------------------------------------------------
📄 Linting: /home/mbc/projects/nf-linter/src/test/test.nf
---------------------------------------------------------
🚩 Errors
- The `script:`, `shell:`, or `exec:` label is required when other sections are present @ line 16, column 5.
- The `script:`, `shell:`, or `exec:` label is required when other sections are present @ line 32, column 5.
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
⚠️ Warnings
- `baseDir` is deprecated and will be removed in a future version @ line 3, column 14
----------------------------------------
Summary for script files
Total files linted: 1
Total errors: 2 🚩
Total warnings: 1 ⚠️
----------------------------------------
```

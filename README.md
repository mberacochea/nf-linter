
# Nextflow Linter

A command-line tool for linting **Nextflow scripts** to detect syntax and semantic issues. Powered by the excellent **Nextflow Language Server tools**.

> **IMPORTANT:**  
> I wrote this mostly for fun, to learn a bit of Groovy, but it could be useful. The Seqera folks will add a linter of sorts into Nextflow itself at some point, so I won't maintain this tool in the long run.

---

## Features

This tool should print exactly the same errors and warnings as the Nextflow VS Code extension.

It supports individual `.nf` files or to recursively lint all `.nf` files in a directory.

> **Note:**  
> It doesn't support configuration files yet, but I may add that at some point.

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
git clone https://github.com/mberacochea/nextflow-linter.git
cd nextflow-linter
./gradlew shadowJar
```

The generated JAR file will be available in the `build/libs` directory:

```plaintext
build/libs/nextflow-linter.jar
```

---

## Usage

### Command Syntax
```bash
java -jar nextflow-linter.jar <script_or_directory_path>
```

### Examples

#### Lint a Single File
```bash
java -jar nextflow-linter.jar /path/to/script.nf
```

#### Lint All `.nf` Files in a Directory
```bash
java -jar nextflow-linter.jar /path/to/scripts/
```

#### Display Help
```bash
java -jar nextflow-linter.jar --help
```

### Output Example
```bash
📁 Linting: /path/to/example.nf
------------------------------------------------------------------------------------------------------
Errors 🚩
- `c` is not defined @ line 66, column 37.
Warnings ⚠️
- Process `when` section will not be supported in a future version - (<> at 27:5)
- Variable was declared but not used - (<> at 70:9)
- Variable was declared but not used - (<> at 32:9)
- Variable was declared but not used - (<> at 36:9)
----------------------------------------
Summary:
Total files linted: 1
Total errors: 1 🚩
Total warnings: 4 ⚠️
----------------------------------------
```

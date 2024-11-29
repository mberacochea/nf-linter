
# Nextflow Linter

A command-line tool for linting **Nextflow scripts and configs** to detect syntax and semantic issues. Powered by the excellent **Nextflow Language Server tools**.

> **IMPORTANT:**  
> I wrote this mostly for fun, to learn a bit of Groovy, but it could be useful. The Seqera folks will add a linter of sorts into Nextflow itself at some point, so I won't maintain this tool in the long run.

---

## Features

This tool prints the same errors and warnings as the Nextflow VS Code extension.

It supports individual `.nf` or `.config` files or recursively linting all `.nf` and `.config` files in a directory.

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

The generated JAR file (`build/libs/nf-linter-all.jar`) will be available in the `build/libs` directory:

```plaintext
build/libs/nf-linter-all.jar
```

---

## Usage

### Command syntax

```bash
java -jar nf-linter-all.jar <script_or_directory_path>
```

### Usage

#### Lint a single file
```bash
java -jar nf-linter-all.jar /path/to/script.nf
```

#### Lint all `.nf` and `.config` files in a directory
```bash
java -jar nf-linter-all.jar /path/to/scripts/
```

#### Display help
```bash
java -jar nf-linter-all.jar --help
```

#### Ignore files using "nf-lint: noqa"

In order to ignore a file, add the comment `// nf-lint: noqa`.

Example
```nextflow
// nf-lint: noqa
workflow {
   PROCESS()
}
```

#### Ignore errors using `noqa`

This linter supports ignoring errors per line or for the whole file.

It follows the Python linter's `noqa` rules, where you can ignore errors in lines using a comment.

#### Ignore an error in one Line

For example, ignore a specific error in one line.

The following script:

```nextflow
process LOOKUP_KINGDOM {
    input:
    tuple val(meta), path(fasta)

    output:
    tuple val(meta), env(value_detected), emit: value_detected

    script:
    """
    value_detected=$(example.py ${meta.id})
    """
}
```

Reports:

```bash
-----------------------------------------------------------------------------
📄 Linting: nf-linter/src/test/resources/test_with_errors_but_noqa_inline.nf
-----------------------------------------------------------------------------
🚩 Errors
- `value_detected` is not defined @ line 6, column 26.
----------------------------------------
Summary for script files
Total files linted: 1
Total errors: 1 🚩
Total warnings: 0 ⚠️
----------------------------------------
```

But it's possible to ignore this error by adding `// noqa` on the line with the error:

```nextflow
process LOOKUP_KINGDOM {
    input:
    tuple val(meta), path(fasta)

    output:
    tuple val(meta), env(value_detected), emit: value_detected // noqa

    script:
    """
    value_detected=$(example.py ${meta.id})
    """
}
```

That error is ignored:

```bash
-----------------------------------------------------------------------------
📄 Linting: nf-linter/src/test/resources/test_with_errors_but_noqa_inline.nf
-----------------------------------------------------------------------------
✨ No issues with this one.
----------------------------------------
Summary for script files
Total files linted: 1
Total errors: 0 🚩
Total warnings: 0 ⚠️
----------------------------------------
```

### Output example

```bash
➜  nf-linter git:(main) java -jar build/libs/nf-linter-all.jar src/test/test.nf 
---------------------------------------------------------
📄 Linting: nf-linter/src/test/test_with_warnings.nf
---------------------------------------------------------
---------------------------------------------------------------------------------
⚠️ Warnings
- Variable was declared but not used @ line 10, column 9
----------------------------------------
Summary for script files
Total files linted: 1
Total errors: 0 🚩
Total warnings: 1 ⚠️
----------------------------------------
```

## TODOs

- [ ] Review the error printing bits, everything is sent to the stdout
- [ ] Automate the nf-lint executable
  generation [really_executable_jars](https://skife.org/java/unix/2011/06/20/really_executable_jars.html)
- [ ] Unit tests for config files
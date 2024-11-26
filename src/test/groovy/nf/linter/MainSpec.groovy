package nf.linter

import spock.lang.*
import nextflow.lsp.services.script.ScriptAstCache

import java.nio.file.Paths

class MainSpec extends Specification {

    def "Test lintFiles with valid files"() {
        given: "A valid nextflow script"

        def testFile = new File(getClass().getResource("/test_all_good.nf").toURI())

        and: "A Script AST Cache"
        def scriptASTCache = new ScriptAstCache()
        scriptASTCache.initialize(Paths.get("").toUri().toString())

        when: "lintFiles is called"
        def result = Main.lintFiles([testFile], scriptASTCache, "test scripts")

        then: "No errors should be returned, and the result should be false"
        !result
    }

    def "Test lintFiles with errors in files"() {
        given: "A nextflow script with excluded syntax -> import declarations"
        def testFile = new File(getClass().getResource("/test_excluded_syntax_import.nf").toURI())

        and: "A Script AST Cache"
        def scriptASTCache = new ScriptAstCache()
        scriptASTCache.initialize(Paths.get("").toUri().toString())

        and:
        def buffer = new ByteArrayOutputStream()
        System.out = new PrintStream(buffer)

        when: "lintFiles is called"
        def result = Main.lintFiles([testFile], scriptASTCache, "test scripts")

        then: "Errors should be returned, and the result should be true"
        result

        expect:
        def stdout = buffer.toString()
        stdout.toString().contains("Groovy `import` declarations are not supported -- use fully-qualified name inline instead @ line 1, column 1.")
        stdout.toString().contains("Total errors: 3")
    }

    def "Test lintFiles with errors in files"() {
        given: "A nextflow script with excluded syntax -> mixing script declarations and statements"
        def testFile = new File(getClass().getResource("/test_excluded_syntax_mixing_script_declarations_and_statements.nf").toURI())

        and: "A Script AST Cache"
        def scriptASTCache = new ScriptAstCache()
        scriptASTCache.initialize(Paths.get("").toUri().toString())

        and:
        def buffer = new ByteArrayOutputStream()
        System.out = new PrintStream(buffer)

        when: "lintFiles is called"
        def result = Main.lintFiles([testFile], scriptASTCache, "test scripts")

        then: "Errors should be returned, and the result should be true"
        result

        expect:
        def stdout = buffer.toString()
        stdout.toString().contains("Statements cannot be mixed with script declarations -- move statements into a process or workflow @ line 7, column 1.")
        stdout.toString().contains("Total errors: 1")
        stdout.toString().contains("Total warnings: 0")
    }

    def "Test call with non-existent path"() {
        given: "An invalid path for the Main class"
        def main = new Main()
        main.path = "nonexistent-path"

        when: "call is executed"
        def result = main.call()

        then: "An error message should be printed and exit code should be 1"
        result == 1
    }
}

// Dummy classes for testing purposes
class DummyLintError {
    private final String message

    DummyLintError(String message) {
        this.message = message
    }

    String getMessage() {
        return message
    }
}

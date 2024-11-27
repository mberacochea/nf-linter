package nf.linter

import spock.lang.Specification
import nextflow.lsp.services.script.ScriptAstCache

import java.nio.file.Paths

class MainSpec extends Specification {
    private ScriptAstCache scriptASTCache

    def setup() {
        scriptASTCache = new ScriptAstCache()
        scriptASTCache.initialize(Paths.get("").toUri().toString())
    }

    def "lintFiles should return false for valid Nextflow scripts"() {
        given: "A valid nextflow script"
        def testFile = new File(getClass().getResource("/test_all_good.nf").toURI())

        when: "lintFiles is called"
        def result = Main.lintFiles([testFile], scriptASTCache, "test scripts")

        then: "No errors should be returned"
        !result
    }

    def "lintFiles should detect and report import declaration errors"() {
        given: "A nextflow script with excluded import syntax"
        def testFile = new File(getClass().getResource("/test_excluded_syntax_import.nf").toURI())

        and: "Capture system output"
        def originalOut = System.out
        def outputStream = new ByteArrayOutputStream()
        System.out = new PrintStream(outputStream)

        when: "lintFiles is called"
        def result = Main.lintFiles([testFile], scriptASTCache, "test scripts")

        then: "Errors should be returned"
        result

        and: "Verify error messages"
        def capturedOutput = outputStream.toString()
        capturedOutput.contains("Groovy `import` declarations are not supported")
        capturedOutput.contains("Total errors: 3")

        cleanup: "Restore system output"
        System.out = originalOut
    }

    def "lintFiles should detect mixing of script declarations and statements"() {
        given: "A nextflow script mixing script declarations and statements"
        def testFile = new File(getClass().getResource("/test_excluded_syntax_mixing_script_declarations_and_statements.nf").toURI())

        and: "Capture system output"
        def originalOut = System.out
        def outputStream = new ByteArrayOutputStream()
        System.out = new PrintStream(outputStream)

        when: "lintFiles is called"
        def result = Main.lintFiles([testFile], scriptASTCache, "test scripts")

        then: "Errors should be returned"
        result

        and: "Verify specific error messages"
        def capturedOutput = outputStream.toString()
        capturedOutput.contains("Statements cannot be mixed with script declarations")
        capturedOutput.contains("Total errors: 1")
        capturedOutput.contains("Total warnings: 0")

        cleanup: "Restore system output"
        System.out = originalOut
    }

    def "lintFiles should detect inline assignment in method call"() {
        given: "A Nextflow script with inline assignment"
        def testScript = File.createTempFile("test", ".nf")
        testScript.deleteOnExit()
        testScript.write('''
            def foo (a, b) {
                println "Hello"
            }
            
            workflow {    
                foo(x=1, y=2)
            }
        '''.stripIndent())

        and: "Capture system output"
        def originalOut = System.out
        def outputStream = new ByteArrayOutputStream()
        System.out = new PrintStream(outputStream)

        when: "lintFiles is called"
        def result = Main.lintFiles([testScript], scriptASTCache, "test scripts")

        then: "Errors should be returned"
        result

        and: "Verify error messages"
        def capturedOutput = outputStream.toString()
        capturedOutput.contains("Unexpected input: '=' @ line 7, column 14.")

        cleanup: "Restore system output"
        System.out = originalOut
    }

    def "lintFiles should detect increment operator usage"() {
        given: "A Nextflow script with increment operator"
        def testScript = File.createTempFile("test", ".nf")
        testScript.deleteOnExit()
        testScript.write('''
            def x = 0
            x++
            println x
        '''.stripIndent())

        and: "Capture system output"
        def originalOut = System.out
        def outputStream = new ByteArrayOutputStream()
        System.out = new PrintStream(outputStream)

        when: "lintFiles is called"
        def result = Main.lintFiles([testScript], scriptASTCache, "test scripts")

        then: "Errors should be returned"
        result

        and: "Verify error messages"
        def capturedOutput = outputStream.toString()
        capturedOutput.contains("Unexpected input: '\\n' @ line 3, column 8.")

        cleanup: "Restore system output"
        System.out = originalOut
    }

    def "lintFiles should accept proper assignment and increment/decrement alternatives"() {
        given: "A Nextflow script with correct assignments"
        def testScript = File.createTempFile("test", ".nf")
        testScript.deleteOnExit()
        testScript.write('''
            workflow {
                def x = 0
                x += 1  // Correct alternative to x++
                x -= 1  // Correct alternative to x--
            }
        '''.stripIndent())

        when: "lintFiles is called"
        def result = Main.lintFiles([testScript], scriptASTCache, "test scripts")

        then: "No errors should be returned"
        !result
    }


    def "call the linter with a file with warnings and silence warnings"() {
        given: "A Main instance with silence warnings"
        def main = new Main()
        main.path = getClass().getResource("/test_with_warnings.nf").path
        main.silenceWarnings = true

        and: "Capture system output"
        def originalOut = System.out
        def outputStream = new ByteArrayOutputStream()
        System.out = new PrintStream(outputStream)

        when: "call is executed"
        def result = main.call()

        then: "No errors should be reported"
        result == 0
        def capturedOutput = outputStream.toString()
        !capturedOutput.contains("Total warnings: 0")

        cleanup: "Restore system output"
        System.out = originalOut
    }

    def "call the linter with an non-existent paths should exit gracefully"() {
        given: "A Main instance with an invalid path"
        def main = new Main()
        main.path = "nonexistent-path"

        when: "call is executed"
        def result = main.call()

        then: "An error should be returned with exit code 1"
        result == 1
    }
}
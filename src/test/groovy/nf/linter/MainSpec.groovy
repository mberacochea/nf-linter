package nf.linter

import spock.lang.Specification
import nextflow.lsp.services.script.ScriptAstCache

import java.nio.file.Paths

class MainSpec extends Specification {

    ScriptAstCache scriptASTCache
    ByteArrayOutputStream outputStream
    PrintStream originalOut

    def setup() {
        scriptASTCache = new ScriptAstCache()
        scriptASTCache.initialize(Paths.get("").toUri().toString())
        // Capture system output
        originalOut = System.out
        outputStream = new ByteArrayOutputStream()
        System.out = new PrintStream(outputStream)
    }

    def cleanup() {
        System.out = originalOut
    }

    Main createMainWithPath(String resourcePath, boolean silenceWarnings = false) {
        def main = new Main()
        main.path = getClass().getResource(resourcePath).path
        main.silenceWarnings = silenceWarnings
        return main
    }

    def "lintFiles should return false for valid Nextflow scripts"() {
        given: "A valid nextflow script"
        def testFile = new File(getClass().getResource("/test_all_good.nf").toURI())

        when: "lintFiles is called"
        def messages = Main.lintFiles([testFile], scriptASTCache, Main.SOURCE_TYPE.SCRIPT, false)

        then: "No errors should be returned"
        !Main.checkForErrors(messages)
    }

    def "lintFiles should detect and report import declaration errors"() {
        given: "A nextflow script with excluded import syntax"
        def testFile = new File(getClass().getResource("/test_excluded_syntax_import.nf").toURI())

        when: "lintFiles is called"
        def messages = Main.lintFiles([testFile], scriptASTCache, Main.SOURCE_TYPE.SCRIPT, false)
        Main.printMessages(messages, false)

        then: "Errors should be returned"
        Main.checkForErrors(messages)

        and: "Verify error messages"
        def capturedOutput = outputStream.toString()
        capturedOutput.contains("Groovy `import` declarations are not supported")
        capturedOutput.contains("Total errors: 3")
    }

    def "lintFiles should detect mixing of script declarations and statements"() {
        given: "A nextflow script mixing script declarations and statements"
        def testFile = new File(getClass().getResource("/test_excluded_syntax_mixing_script_declarations_and_statements.nf").toURI())

        when: "lintFiles is called"
        def messages = Main.lintFiles([testFile], scriptASTCache, Main.SOURCE_TYPE.SCRIPT, false)
        Main.printMessages(messages, false)

        then: "Errors should be returned"
        Main.checkForErrors(messages)

        and: "Verify specific error messages"
        def capturedOutput = outputStream.toString()
        capturedOutput.contains("Statements cannot be mixed with script declarations")
        capturedOutput.contains("Total errors: 1")
        capturedOutput.contains("Total warnings: 0")
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

        when: "lintFiles is called"
        def messages = Main.lintFiles([testScript], scriptASTCache, Main.SOURCE_TYPE.SCRIPT, false)
        Main.printMessages(messages, false)

        then: "Errors should be returned"
        Main.checkForErrors(messages)

        and: "Verify error messages"
        def capturedOutput = outputStream.toString()
        capturedOutput.contains("Unexpected input: '=' @ line 7, column 14.")
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

        when: "lintFiles is called"
        def messages = Main.lintFiles([testScript], scriptASTCache, Main.SOURCE_TYPE.SCRIPT, false)
        Main.printMessages(messages, false)

        then: "Errors should be returned"
        Main.checkForErrors(messages)

        and: "Verify error messages"
        def capturedOutput = outputStream.toString()
        capturedOutput.contains("Unexpected input: '\\n' @ line 3, column 8.")
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
        def messages = Main.lintFiles([testScript], scriptASTCache, Main.SOURCE_TYPE.SCRIPT, false)

        then: "No errors should be returned"
        !Main.checkForErrors(messages)
    }

    def "launch the tool with a folder that only has a txt file"() {
        given: "An folder that doesn't have any .nf or .config files"
        def testFolder = File.createTempDir("test_empty")
        testFolder.deleteOnExit()
        def textScript = File.createTempFile("test_text", ".txt", testFolder)
        textScript.deleteOnExit()
        textScript.write("Definitely not a Nextflow script or config file")
        def main = new Main()
        main.path = testFolder.path

        when: "call is executed"
        def result = main.call()

        then: "An error should be returned with exit code 1"
        result == 1
        def capturedOutput = outputStream.toString()
        capturedOutput.contains("Info: No .nf or .config files found in the specified path: '${testFolder.path}'.")
    }

    def "call the linter with a file with warnings and silence warnings"() {
        given: "A Main instance with silence warnings"
        def main = createMainWithPath("/test_with_warnings.nf")

        when: "call is executed"
        def result = main.call()

        then: "No errors should be reported"
        result == 0
        def capturedOutput = outputStream.toString()
        capturedOutput.contains("Total warnings: 0")
        capturedOutput.contains("Total warnings: 1")
    }

    def "call the linter on a file that has nf-lint: noqa"() {
        given: "A Main instance"
        def main = createMainWithPath("/test_with_errors_but_noqa.nf")

        when: "call is executed"
        def result = main.call()

        then: "The file won't be linted"
        result == 0
        def capturedOutput = outputStream.toString()
        capturedOutput.contains("No script files to lint.")
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
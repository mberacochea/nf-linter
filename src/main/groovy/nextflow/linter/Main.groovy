package nextflow.linter

import nextflow.lsp.services.script.ScriptAstCache

import org.codehaus.groovy.control.CompilerConfiguration
import java.nio.file.Paths

class Main {

    static void lint(String scriptPath) {

        def srcFile = new File(scriptPath)
        if ( !srcFile.exists() ) {
            println "Error: File '${scriptPath}' does not exist."
            return
        }

        // This is the service that does all the heavy lifting
        def scriptASTCache = new ScriptAstCache()
        scriptASTCache.initialize(Paths.get("").toUri().toString())

        // Dummy override of the NF LSP File Cache, it doesn't cache anything :)
        def dummyCacheFile = new DummyFileCache()
        def uri = srcFile.toURI()
        dummyCacheFile.setContents(uri, srcFile.text)

        // The update method will run the parsing and all the heavy lifting
        Set uris = [uri]
        scriptASTCache.update(uris, dummyCacheFile)

        // Print errors and warnings
        scriptASTCache.getErrors(uri).each { error ->
            println "${error.getMessage()}"
        }
        scriptASTCache.getWarnings(uri).each { warning ->
            println "${warning.getMessage()}"
        }
    }

    static void main(String[] args) {
        if (args.length == 0) {
            println "Usage: nextflow-linter <script.nf>"
            return
        }
        lint(args[0])
    }
}

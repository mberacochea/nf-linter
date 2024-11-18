package nextflow.linter

import nextflow.lsp.file.FileCache
import nextflow.lsp.services.script.ScriptAstCache
import picocli.CommandLine
import java.nio.file.Paths

import org.fusesource.jansi.AnsiConsole
import org.fusesource.jansi.Ansi


@CommandLine.Command(
        name = "nf-lint",
        mixinStandardHelpOptions = true,
        version = "nextflow-linter 0.0.1",
        description = "Lints Nextflow scripts for syntax and semantic issues using the Nextflow Language Server tools."
)
class Main implements Runnable {

    @CommandLine.Parameters(index = "0", description = "The Nextflow script or directory to lint.")
    private String path

    @Override
    void run() {
        def src = new File(path)
        if (!src.exists()) {
            println "\u001B[31mError: Path '${path}' does not exist.\u001B[0m"
            return
        }

        // Initialize the ScriptAstCache
        def scriptASTCache = new ScriptAstCache()
        scriptASTCache.initialize(Paths.get("").toUri().toString())

        // Create and populate the file list
        def fileList = []
        if (src.isDirectory()) {
            src.eachFileRecurse { file ->
                if (file.name.endsWith(".nf")) {
                    fileList << file
                }
            }
        } else {
            fileList << src
        }

        if (fileList.isEmpty()) {
            println "No .nf files found in the specified path: '${path}'."
            return
        }

        // Lint the files
        lintFiles(fileList, scriptASTCache)
    }

    private static void lintFiles(List<File> files, ScriptAstCache scriptASTCache) {
        if (files.isEmpty()) {
            println Ansi.ansi().fgBright(Ansi.Color.RED).a("No Nextflow files found to lint.").reset()
            return
        }

        // Dummy override of the FileCache
        def dummyCacheFile = new DummyFileCache()

        // Convert files to URIs and add them to the dummy cache
        def uris = files.collect { file ->
            def uri = file.toURI()
            dummyCacheFile.setContents(uri, file.text)
            uri
        } as Set

        // Pass the URIs to the ScriptAstCache for parsing and linting
        try {
            scriptASTCache.update(uris, dummyCacheFile as FileCache)
        } catch (Exception e) {
            println Ansi.ansi().fgBright(Ansi.Color.RED).a("Error processing files: ${e.message}").reset()
            e.printStackTrace()
            return
        }

        def totalErrors = 0
        def totalWarnings = 0

        // Print linting results for each file
        uris.each { uri ->
            println Ansi.ansi().fgBright(Ansi.Color.BLUE).a("📁 Linting: ${new File(uri).path}").reset()
            println "-" * ( uri.toString().length() + 11 ) // to account for the linting prefix

            if (scriptASTCache.hasErrors(uri)) {
                println Ansi.ansi().fgBright(Ansi.Color.RED).a("Errors 🚩").reset()
                scriptASTCache.getErrors(uri).each { error ->
                    println "- ${error.getMessage()}"
                }
            }

            if (scriptASTCache.hasWarnings(uri)) {
                println Ansi.ansi().fgBright(Ansi.Color.YELLOW).a("Warnings ⚠️").reset()
                // TODO: improve, i.e. for unused variable we need to print the name of the variable and the position at least
                scriptASTCache.getWarnings(uri).each { warning ->
                    println "- ${warning.getMessage()} - ${warning.getContext()}"
                }
            }

            if (!scriptASTCache.hasWarnings(uri) && !scriptASTCache.hasErrors(uri)) {
                println Ansi.ansi().fgBright(Ansi.Color.GREEN).a("✨ No issues with this one.").reset()
            }

            totalErrors += scriptASTCache.getErrors(uri).size()
            totalWarnings += scriptASTCache.getWarnings(uri).size()
            println "\n"
        }

        println "-" * 40
        println Ansi.ansi().fgBright(Ansi.Color.BLUE).a("Summary:").reset()
        println "Total files linted: ${files.size()}"
        println Ansi.ansi().fgBright(Ansi.Color.RED).a("Total errors: ${totalErrors} 🚩").reset()
        println Ansi.ansi().fgBright(Ansi.Color.YELLOW).a("Total warnings: ${totalWarnings} ⚠️").reset()
        println "-" * 40
    }

    static void main(String[] args) {
        AnsiConsole.systemInstall()
        int exitCode = new CommandLine(new Main()).execute(args)
        AnsiConsole.systemUninstall()
        System.exit(exitCode)
    }
}

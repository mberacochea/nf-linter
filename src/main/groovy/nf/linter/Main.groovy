package nf.linter

import nextflow.lsp.file.FileCache
import nextflow.lsp.services.script.ScriptAstCache
import nextflow.lsp.services.config.ConfigAstCache
import org.fusesource.jansi.Ansi
import picocli.CommandLine

import java.nio.file.Paths
import java.util.concurrent.Callable

@CommandLine.Command(
        name = "nf-lint",
        mixinStandardHelpOptions = true,
        version = "nf-linter 0.0.1",
        description = "Lints Nextflow scripts for syntax and semantic issues using the Nextflow Language Server tools."
)
class Main implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "The Nextflow script or directory to lint.")
    private String path

    private static final String NF_EXTENSION = ".nf"
    private static final String CONFIG_EXTENSION = ".config"

    @Override
    Integer call() {
        def src = new File(path)
        if (!src.exists()) {
            println Ansi.ansi().fgBright(Ansi.Color.RED).a("Error: Path '${path}' does not exist.").reset()
            return 1
        }

        if (!src.isFile() && !src.isDirectory()) {
            println Ansi.ansi().fgBright(Ansi.Color.RED).a("Error: Path '${path}' is invalid.").reset()
            return 1
        }

        def filesMap = collectFiles(src)
        def scriptFiles = filesMap.scriptFiles
        def configFiles = filesMap.configFiles

        if (scriptFiles.isEmpty() && configFiles.isEmpty()) {
            println Ansi.ansi().fgBright(Ansi.Color.YELLOW).a("Info: No .nf or .config files found in the specified path: '${path}'.").reset()
            return 1
        }

        // Initialize AST caches
        def scriptASTCache = new ScriptAstCache()
        scriptASTCache.initialize(Paths.get("").toUri().toString())

        def errorsInScripts = scriptFiles.isEmpty() ? 0 : lintFiles(scriptFiles, scriptASTCache, "script files")
        def errorsInConfigs = configFiles.isEmpty() ? 0 : lintFiles(configFiles, new ConfigAstCache(), "config files")

        return errorsInScripts || errorsInConfigs ? 1 : 0
    }

    private static Map<String, List<File>> collectFiles(File src) {
        def scriptFiles = []
        def configFiles = []
        def fileExtensions = [(NF_EXTENSION): scriptFiles, (CONFIG_EXTENSION): configFiles]

        if (src.isDirectory()) {
            src.eachFileRecurse { file ->
                fileExtensions.each { ext, list ->
                    if (file.name.endsWith(ext)) list << file
                }
            }
        } else if (src.isFile()) {
            fileExtensions.each { ext, list ->
                if (src.name.endsWith(ext)) list << src
            }
        }

        return [scriptFiles: scriptFiles, configFiles: configFiles]
    }

    private static boolean lintFiles(List<File> files, def astCache, String label) {
        if (files.isEmpty()) {
            println Ansi.ansi().fgBright(Ansi.Color.RED).a("Error: No ${label} files to lint.").reset()
            return false
        }

        def dummyCacheFile = new DummyFileCache()
        def uris = files.collect { file ->
            def uri = file.toURI()
            dummyCacheFile.setContents(uri, file.text)
            uri
        } as Set

        try {
            astCache.update(uris, dummyCacheFile)
        } catch (Exception e) {
            println Ansi.ansi().fgBright(Ansi.Color.RED).a("Error: Error processing files: ${e.message}").reset()
            e.printStackTrace()
            return true
        }

        def totalErrors = 0
        def totalWarnings = 0

        uris.each { uri ->
            def filePath = new File(uri).path
            println "-" * (12 + filePath.length())
            println Ansi.ansi().fgBright(Ansi.Color.BLUE).a("📄 Linting: ${filePath}").reset()
            println "-" * (12 + filePath.length())

            if (astCache.hasErrors(uri)) {
                println Ansi.ansi().fgBright(Ansi.Color.RED).a("🚩 Errors").reset()
                astCache.getErrors(uri).each { error ->
                    println "- ${error.getMessage()}"
                }
                if (astCache.hasWarnings(uri)) println "~" * (12 + filePath.length())
            }

            if (astCache.hasWarnings(uri)) {
                println Ansi.ansi().fgBright(Ansi.Color.YELLOW).a("⚠️ Warnings").reset()
                astCache.getWarnings(uri).each { warning ->
                    def context = warning.getContext()
                    println "- ${warning.getMessage()} @ line ${context.getStartLine()}, column ${context.getStartColumn()}"
                }
            }

            if (!astCache.hasWarnings(uri) && !astCache.hasErrors(uri)) {
                println Ansi.ansi().fgBright(Ansi.Color.GREEN).a("✨ No issues with this one.").reset()
            }

            totalErrors += astCache.getErrors(uri).size()
            totalWarnings += astCache.getWarnings(uri).size()
        }

        println "-" * 40
        println Ansi.ansi().fgBright(Ansi.Color.BLUE).a("Summary for ${label}").reset()
        println "Total files linted: ${files.size()}"
        println Ansi.ansi().fgBright(Ansi.Color.RED).a("Total errors: ${totalErrors} 🚩").reset()
        println Ansi.ansi().fgBright(Ansi.Color.YELLOW).a("Total warnings: ${totalWarnings} ⚠️").reset()
        println "-" * 40

        return totalErrors > 0
    }

    static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args)
        System.exit(exitCode)
    }
}

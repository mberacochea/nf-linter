package nf.linter

import nextflow.lsp.services.script.ScriptAstCache
import nextflow.lsp.services.config.ConfigAstCache
import org.fusesource.jansi.Ansi
import picocli.CommandLine

import java.nio.file.Paths
import java.nio.file.Files
import java.util.concurrent.Callable

@CommandLine.Command(
        name = "nf-lint",
        mixinStandardHelpOptions = true,
        version = "nf-linter 0.1.0beta",
        description = "Lints Nextflow scripts for syntax and semantic issues using the Nextflow Language Server tools."
)
class Main implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "The Nextflow script or directory to lint.")
    private String path

    @CommandLine.Option(names = ['-w', '--silence-warnings'], description = 'Silence warnings')
    private Boolean silenceWarnings = false

    private static final String NF_EXTENSION = ".nf"
    private static final String CONFIG_EXTENSION = ".config"
    static final enum SOURCE_TYPE {
        SCRIPT,
        CONFIG
    }

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

        def errorsInScripts = scriptFiles.isEmpty() ? 0 : lintFiles(scriptFiles, scriptASTCache, SOURCE_TYPE.SCRIPT, silenceWarnings)
        def errorsInConfigs = configFiles.isEmpty() ? 0 : lintFiles(configFiles, new ConfigAstCache(), SOURCE_TYPE.CONFIG, silenceWarnings)

        return errorsInScripts || errorsInConfigs ? 1 : 0
    }

    /**
     * Collect all the nf script and config files, it will crawl if the path is a directory
     * @param src -> Source File | Directory
     * @return [List<Scripts>, List<Configs>]
     */
    private static Map<String, List<File>> collectFiles(File src) {
        def scriptFiles = []
        def configFiles = []
        def fileExtensions = [(NF_EXTENSION): scriptFiles, (CONFIG_EXTENSION): configFiles]

        if (src.isDirectory()) {
            src.eachFileRecurse { file ->
                fileExtensions.forEach { ext, list ->
                    if (file.name.endsWith(ext)) list << file
                }
            }
        } else if (src.isFile()) {
            fileExtensions.forEach { ext, list ->
                if (src.name.endsWith(ext)) list << src
            }
        }

        return [scriptFiles: scriptFiles, configFiles: configFiles]
    }

    /**
     * Get the line "startLine" from a file
     *
     * @param filePath Path to the text file
     * @param startLine Line number to read (1-based index)
     * @param endLine Line number to read (1-based index)
     * @return The content of the specified line, or null if line doesn't exist
     * @throws IOException If there's an error reading the file
     */
    static String readLineFromFile(String filePath, int startLine) {
        try {
            Files.lines(Paths.get(filePath)).skip(startLine - 1).findFirst().orElse(null)
        } catch (IOException e) {
            throw new IOException("Error reading file: ${filePath}", e)
        }
    }

    /**
     * Use the Nextflow Language Server to lint the files
     * @param files a List of Files
     * @param astCache the ASTCacheService (either Script or Config [as in Nextflow config files]
     * @param SOURCE_TYPE Source type, either a script or a config
     * @param silenceWarnings Set to true to disable the warning messages
     * @return True if there are any errors in the linted files
     */
    static boolean lintFiles(List<File> files, def astCache, SOURCE_TYPE sourceType, Boolean silenceWarnings = false) {
        def label = sourceType.toString().toLowerCase()

        if (files.isEmpty()) {
            println Ansi.ansi().fgBright(Ansi.Color.RED).a("Error: No ${label} files to lint.").reset()
            return false
        }

        def dummyCacheFile = new DummyFileCache()
        def uris = new HashSet<URI>()
        files.each { file ->
            def uri = file.toURI()
            // Don't lint files that have // nf-lint: noqa in the first 10 lines - ignore
            if (!file.text.contains("// nf-lint: noqa")) {
                dummyCacheFile.setContents(uri, file.text)
                uris << uri
            }
        }

        if (!uris.size()) {
            println Ansi.ansi().fgBright(Ansi.Color.GREEN).a("No ${label} files to lint.").reset()
            return false
        }

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
            def filePath = new File(uri as URI).path
            println "-" * (12 + filePath.length())
            println Ansi.ansi().fgBright(Ansi.Color.BLUE).a("📄 Linting: ${filePath}").reset()
            println "-" * (12 + filePath.length())

            def errorsInFile = []
            if (astCache.hasErrors(uri)) {
                astCache.getErrors(uri).each { error ->
                    def statement = readLineFromFile(filePath, error.getLine() as int)
                    if (!statement.contains("// noqa")) {
                        errorsInFile << "- ${error.getMessage()}"
                        totalErrors += 1
                    }
                }
                if (astCache.hasWarnings(uri)) println "~" * (12 + filePath.length())
            }

            if (errorsInFile.size()) {
                println Ansi.ansi().fgBright(Ansi.Color.RED).a("🚩 Errors").reset()
                errorsInFile.each(errorMessage -> {
                    println errorMessage
                })
            }

            if (!silenceWarnings && astCache.hasWarnings(uri)) {
                println Ansi.ansi().fgBright(Ansi.Color.YELLOW).a("⚠️ Warnings").reset()
                astCache.getWarnings(uri).each { warning ->
                    def context = warning.getContext()
                    println "- ${warning.getMessage()} @ line ${context.getStartLine()}, column ${context.getStartColumn()}"
                    totalWarnings += 1
                }
            }

            if (totalErrors == 0 && totalWarnings == 0) {
                println Ansi.ansi().fgBright(Ansi.Color.GREEN).a("✨ No issues with this one.").reset()
            }
        }

        println "-" * 40
        println Ansi.ansi().fgBright(Ansi.Color.BLUE).a("Summary for ${label}").reset()
        println "Total files linted: ${files.size()}"
        println Ansi.ansi().fgBright(Ansi.Color.RED).a("Total errors: ${totalErrors} 🚩").reset()
        if (!silenceWarnings) {
            println Ansi.ansi().fgBright(Ansi.Color.YELLOW).a("Total warnings: ${totalWarnings} ⚠️").reset()
        }
        println "-" * 40

        return totalErrors > 0
    }

    static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args)
        System.exit(exitCode)
    }
}

package nf.linter

import nextflow.lsp.services.config.ConfigAstCache
import nextflow.lsp.services.script.ScriptAstCache
import org.fusesource.jansi.Ansi
import picocli.CommandLine

import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Callable

class FileLinterMessages {
    File file
    Main.SOURCE_TYPE sourceType
    Boolean silenceWarnings
    ArrayList<LinterMessage> errorMessages
    ArrayList<LinterMessage> warningMessages

    FileLinterMessages(File file, Main.SOURCE_TYPE sourceType, Boolean silenceWarnings) {
        this.file = file
        this.sourceType = sourceType
        this.silenceWarnings = silenceWarnings
        this.errorMessages = []
        this.warningMessages = []
    }

    /**
     * Return the file Path of the file
     * @return
     */
    String filePath() {
        return this.file.path
    }

    /**
     * Add one more error to the file
     * @param message
     */
    def addErrorMessage(LinterMessage message) {
        this.errorMessages << message
    }

    /**
     * Add one warning error to the file
     * @param message
     */
    def addWarningMessage(LinterMessage message) {
        this.warningMessages << message
    }

    /**
     * Print the file lint results in the terminal
     * @return
     */
    def print() {
        def filePath = this.filePath()

        println "-" * (9 + filePath.length())
        println Ansi.ansi().fgBright(Ansi.Color.BLUE).a("Linting: ${filePath}").reset()
        println "-" * (9 + filePath.length())

        if (this.errorMessages.any()) {
            println Ansi.ansi().fgBright(Ansi.Color.RED).a("Errors").reset()
            this.errorMessages.forEach { message ->
                {
                    message.print()
                }
            }
        } else {
            println Ansi.ansi().fgBright(Ansi.Color.GREEN).a("No errors with this one").reset()
        }

        if (this.warningMessages.any() && !this.silenceWarnings) {
            println Ansi.ansi().fgBright(Ansi.Color.YELLOW).a("Warnings").reset()
            this.warningMessages.forEach { message ->
                {
                    message.print()
                }
            }
        }
    }
}


/**
 * Simple class to store the linter messages to be print
 */
class LinterMessage {

    enum TYPE {
        ERROR,
        WARNING
    }

    // TODO: get the type of this thing
    def message
    TYPE messageType

    LinterMessage(def message, TYPE messageType) {
        this.message = message
        this.messageType = messageType
    }

    /**
     * Print the message as <message string> @ line <line>, column <column>
     */
    def print() {
        if (this.messageType == TYPE.ERROR) {
            println "${this.message.getMessage()}"
        }
        if (this.messageType == TYPE.WARNING) {
            def context = this.message.getContext()
            println "${this.message.getMessage()} @ line ${context.getStartLine()}, column ${context.getStartColumn()}"
        }
    }
}


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
            System.err.println Ansi.ansi().fgBright(Ansi.Color.RED).a("Error: Path '${path}' does not exist.").reset()
            return 1
        }

        if (!src.isFile() && !src.isDirectory()) {
            System.err.println Ansi.ansi().fgBright(Ansi.Color.RED).a("Error: Path '${path}' is invalid.").reset()
            return 1
        }

        def filesMap = collectFiles(src)
        def scriptFiles = filesMap.scriptFiles
        def configFiles = filesMap.configFiles

        if (scriptFiles.isEmpty() && configFiles.isEmpty()) {
            println Ansi.ansi().fgBright(Ansi.Color.YELLOW).a("Info: No .nf or .config files found in the specified path: '${path}'.").reset()
            return 1
        }

        def messages = []

        if (!scriptFiles.isEmpty()) {
            ScriptAstCache scriptASTCache = new ScriptAstCache()
            scriptASTCache.initialize(Paths.get("").toUri().toString())
            messages.addAll(lintFiles(scriptFiles, scriptASTCache, SOURCE_TYPE.SCRIPT, silenceWarnings))
        }

        if (!configFiles.isEmpty()) {
            def configAstCache = new ConfigAstCache();
            messages.addAll(lintFiles(configFiles, configAstCache, SOURCE_TYPE.CONFIG, silenceWarnings))
        }

        // Print them //
        printMessages(messages, silenceWarnings)

        Boolean anyErrors = (messages as List<FileLinterMessages>).any { fileLintMessages -> fileLintMessages.errorMessages.any() }

        return anyErrors ? 1 : 0
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
     * @return A LinterMessages per file Map
     */
    static ArrayList<FileLinterMessages> lintFiles(ArrayList<File> files, def astCache, SOURCE_TYPE sourceType, Boolean silenceWarnings) {
        def label = sourceType.toString().toLowerCase()

        def linterMessages = []

        if (files.isEmpty()) {
            System.err.println Ansi.ansi().fgBright(Ansi.Color.RED).a("Error: No ${label} files to lint.").reset()
            return []
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
            return []
        }

        try {
            astCache.update(uris, dummyCacheFile)
        } catch (Exception e) {
            println Ansi.ansi().fgBright(Ansi.Color.RED).a("Error: Error processing files: ${e.message}").reset()
            e.printStackTrace()
            return []
        }

        uris.forEach { uri ->
            def file = new File(uri as URI)
            def filePath = file.path
            def fileLinterMessages = new FileLinterMessages(file, sourceType, silenceWarnings)

            if (astCache.hasErrors(uri)) {
                astCache.getErrors(uri).forEach { error ->
                    def statement = readLineFromFile(filePath, error.getLine() as int)
                    if (!statement.contains("// noqa")) {
                        fileLinterMessages.addErrorMessage(new LinterMessage(error, LinterMessage.TYPE.ERROR))
                    }
                }
            }

            if (astCache.hasWarnings(uri)) {
                astCache.getWarnings(uri).forEach { warning ->
                    fileLinterMessages.addWarningMessage(new LinterMessage(warning, LinterMessage.TYPE.WARNING))
                }
            }

            linterMessages << fileLinterMessages
        }

        return linterMessages
    }

    /**
     * Print the File Linter Messages in the terminal
     * @param messages
     */
    static def printMessages(List<FileLinterMessages> fileLinterMessages, Boolean silenceWarnings) {

        def summary = [
                lintedScripts : 0,
                scriptErrors  : 0,
                scriptWarnings: 0,
                lintedConfigs : 0,
                configErrors  : 0,
                configWarnings: 0,
        ]

        fileLinterMessages.forEach { linterMessage ->
            {
                if (linterMessage.sourceType == SOURCE_TYPE.SCRIPT) {
                    summary.lintedScripts++
                    summary.scriptErrors += linterMessage.errorMessages.size()
                    summary.scriptWarnings += linterMessage.warningMessages.size()
                }
                if (linterMessage.sourceType == SOURCE_TYPE.CONFIG) {
                    summary.lintedConfigs++
                    summary.configErrors += linterMessage.errorMessages.size()
                    summary.configWarnings += linterMessage.warningMessages.size()
                }
                linterMessage.print()
            }
        }

        println "-" * 40
        println Ansi.ansi().fgBright(Ansi.Color.BLUE).a("Summary").reset()
        println "Total script files linted: ${summary.lintedScripts}"
        println Ansi.ansi().fgBright(Ansi.Color.RED).a("Total errors: ${summary.scriptErrors}").reset()
        if (!silenceWarnings) {
            println Ansi.ansi().fgBright(Ansi.Color.YELLOW).a("Total warnings: ${summary.scriptWarnings}️").reset()
        }
        println ""
        println "Total config files linted️: ${summary.lintedConfigs}"
        println Ansi.ansi().fgBright(Ansi.Color.RED).a("Total errors: ${summary.configErrors}").reset()
        if (!silenceWarnings) {
            println Ansi.ansi().fgBright(Ansi.Color.YELLOW).a("Total warnings: ${summary.configWarnings}️").reset()
        }
        println "-" * 40
    }

    static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args)
        System.exit(exitCode)
    }
}

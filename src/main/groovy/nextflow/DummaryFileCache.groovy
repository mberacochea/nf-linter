package nextflow.linter

import nextflow.lsp.file.FileCache

class DummyFileCache extends FileCache {

    private final Map<String, String> fileContents = [:]

    @Override
    public boolean isOpen(URI uri) {
        // Always return true to simulate open files
        return true
    }

    @Override
    public String getContents(URI uri) {
        // Return the stored content or throw an error if not found
        if (!fileContents.containsKey(uri)) {
            throw new IllegalArgumentException("No content found for URI: ${uri}")
        }
        return fileContents[uri]
    }

    @Override
    public void setContents(URI uri, String contents) {
        // Add file content to the dummy cache
        fileContents[uri] = contents
    }
}

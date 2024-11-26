
process testProcess {
    """
    echo "Hello, Nextflow!"
    """
}

workflow {
    testProcess()
}

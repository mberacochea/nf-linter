// nf-lint: noqa
process LOOKUP_KINGDOM {
    input:
    tuple val(meta), path(fasta)

    output:
    tuple val(meta), env(value_detected), emit: value_detected

    script:
    """
    value_detected=\$(example.py ${meta.id})
    """
}

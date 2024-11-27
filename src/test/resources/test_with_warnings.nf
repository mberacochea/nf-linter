process TEST {

    input:
    path(entrada)

    output:
    path("test")

    script:
    def variable = 1
    """
    script.py --input ${entrada}
    """

}
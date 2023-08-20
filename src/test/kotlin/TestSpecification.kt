import java.io.File

sealed class TestSpecification(val file: File, val description: String)

class PositiveTest(file: File, description: String?) :
    TestSpecification(file, description ?: "A regular program should pass analysis.")

class NegativeTest(file: File, description: String, val errors: List<String>) :
    TestSpecification(file, description)

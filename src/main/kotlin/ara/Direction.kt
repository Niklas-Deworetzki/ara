package ara

enum class Direction {
    FORWARD, BACKWARD;

    fun <T> choose(fw: T, bw: T): T = when (this) {
        FORWARD -> fw
        BACKWARD -> bw
    }
}
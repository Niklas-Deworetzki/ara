package ara

enum class Direction {
    FORWARD, BACKWARD;

    val inverted: Direction
        get() = choose(BACKWARD, FORWARD)

    fun <T> choose(fw: T, bw: T): T = when (this) {
        FORWARD -> fw
        BACKWARD -> bw
    }
}
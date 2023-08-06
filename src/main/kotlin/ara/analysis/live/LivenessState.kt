package ara.analysis.live

import ara.position.Range

sealed interface LivenessState {

    val initializers: Set<Range>
        get() = emptySet()

    val finalizers: Set<Range>
        get() = emptySet()


    object Unknown : LivenessState

    data class Initialized(override val initializers: Set<Range>) : LivenessState

    data class Finalized(override val finalizers: Set<Range>) : LivenessState

    data class Conflict(override val initializers: Set<Range>, override val finalizers: Set<Range>) : LivenessState

    companion object {
        infix fun LivenessState.meet(other: LivenessState): LivenessState =
            combine(this, other)

        private tailrec fun combine(a: LivenessState, b: LivenessState): LivenessState = when (a) {
            Unknown ->
                b

            is Initialized -> when (b) {
                is Initialized ->
                    Initialized(a.initializers + b.initializers)

                is Finalized ->
                    Conflict(a.initializers, b.finalizers)

                else ->
                    combine(b, a)
            }

            is Finalized -> when (b) {
                is Finalized ->
                    Finalized(a.finalizers + b.finalizers)

                is Initialized ->
                    Conflict(b.initializers, a.finalizers)

                else ->
                    combine(b, a)
            }

            is Conflict -> when (b) {
                is Conflict ->
                    Conflict(a.initializers + b.initializers, a.finalizers + b.finalizers)

                is Initialized ->
                    Conflict(a.initializers + b.initializers, a.finalizers)

                is Finalized ->
                    Conflict(a.initializers, a.finalizers + b.finalizers)

                else ->
                    combine(b, a)
            }
        }
    }
}

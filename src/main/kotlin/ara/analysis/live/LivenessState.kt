package ara.analysis.live

import ara.position.Range

sealed interface LivenessState {

    object Unknown : LivenessState

    class Initialized(val initializers: List<Range>) : LivenessState

    class Finalized(val finalizers: List<Range>) : LivenessState

    class Conflict(val initializers: List<Range>, val finalizers: List<Range>) : LivenessState
}

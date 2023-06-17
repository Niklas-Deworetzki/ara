package ara.analysis.live

import ara.position.Range

sealed interface LivenessState {

    object Unknown : LivenessState

    data class Initialized(val initializers: Set<Range>) : LivenessState

    data class Finalized(val finalizers: Set<Range>) : LivenessState

    data class Conflict(val initializers: Set<Range>, val finalizers: Set<Range>) : LivenessState
}

package ara.interpreter

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

private class InternalInconsistencyException(message: String) : IllegalStateException(message)

private class ReversibilityViolation(message: String) : IllegalStateException(message)

@OptIn(ExperimentalContracts::class)
fun ensureReversibility(condition: Boolean, lazyMessage: () -> String) {
    contract {
        returns() implies condition
        callsInPlace(lazyMessage, InvocationKind.AT_MOST_ONCE)
    }

    if (!condition) {
        throw ReversibilityViolation(lazyMessage())
    }
}

@OptIn(ExperimentalContracts::class)
fun internalAssertion(condition: Boolean, lazyMessage: () -> String) {
    contract {
        returns() implies condition
        callsInPlace(lazyMessage, InvocationKind.AT_MOST_ONCE)
    }

    if (!condition)
        throw InternalInconsistencyException(lazyMessage())
}

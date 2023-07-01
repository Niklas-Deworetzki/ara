package ara.interpreter

class InternalInconsistencyException(message: String) : IllegalStateException(message)

class ReversibilityViolation(message: String) : IllegalStateException(message)

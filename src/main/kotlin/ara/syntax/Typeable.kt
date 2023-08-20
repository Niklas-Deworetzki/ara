package ara.syntax

sealed class Typeable : Syntax() {
    lateinit var computedType: ara.types.Type
}
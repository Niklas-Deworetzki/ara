package ara.utils

sealed class Either<out L, out R> {

    data class Left<L>(val left: L) : Either<L, Nothing>()
    data class Right<R>(val right: R) : Either<Nothing, R>()

    companion object {

        fun <L> left(left: L): Left<L> = Left(left)

        fun <R> right(right: R): Right<R> = Right(right)

    }
}
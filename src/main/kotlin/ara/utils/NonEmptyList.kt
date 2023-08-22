package ara.utils

/**
 * A generic ordered collection of one or more elements.
 *
 * @param E the type of elements contained in the list. The list is covariant in its element type.
 */
class NonEmptyList<out E>
private constructor(val data: List<E>) : AbstractList<E>() {

    override val size: Int
        get() = data.size

    override fun contains(element: @UnsafeVariance E): Boolean =
        data.contains(element)

    override fun containsAll(elements: Collection<@UnsafeVariance E>): Boolean =
        data.containsAll(elements)

    override fun get(index: Int): E =
        data[index]

    override fun indexOf(element: @UnsafeVariance E): Int =
        data.indexOf(element)

    @Deprecated("NonEmptyList.isEmpty() will always return false.", ReplaceWith("true"))
    override fun isEmpty(): Boolean =
        data.isEmpty()

    override fun iterator(): Iterator<E> =
        data.iterator()

    override fun lastIndexOf(element: @UnsafeVariance E): Int =
        data.lastIndexOf(element)

    override fun listIterator(): ListIterator<E> =
        data.listIterator()

    override fun listIterator(index: Int): ListIterator<E> =
        data.listIterator(index)

    override fun subList(fromIndex: Int, toIndex: Int): List<E> =
        data.subList(fromIndex, toIndex)

    inline fun <R> map(transform: (E) -> R): NonEmptyList<R> =
        fromNonEmptyList(data.map(transform))


    companion object {
        fun <E> List<E>.toNonEmptyList(): NonEmptyList<E> =
            fromNonEmptyList(this)

        fun <E> Sequence<E>.toNonEmptyList(): NonEmptyList<E> =
            fromNonEmptyList(this.toList())

        fun <E> fromNonEmptyList(list: List<E>): NonEmptyList<E> {
            assert(list.isNotEmpty()) { "NonEmptyList cannot be empty!" }
            return NonEmptyList(list)
        }
    }
}

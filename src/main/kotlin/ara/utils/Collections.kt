package ara.utils

infix fun <E> Set<E>.intersects(other: Set<E>): Boolean =
    this.any(other::contains)

fun <X, Y> combineWith(xs: Iterable<X>, ys: Iterable<Y>, action: (X, Y) -> Unit) {
    val xsIterator = xs.iterator()
    val ysIterator = ys.iterator()
    while (xsIterator.hasNext() && ysIterator.hasNext()) {
        action(xsIterator.next(), ysIterator.next())
    }
}

fun <X, Y, Z> zip(xs: Iterable<X>, ys: Iterable<Y>, combinator: (X, Y) -> Z): List<Z> {
    val xIterator = xs.iterator()
    val yIterator = ys.iterator()
    val result = mutableListOf<Z>()

    while (xIterator.hasNext() && yIterator.hasNext()) {
        result.add(combinator(xIterator.next(), yIterator.next()))
    }
    return result
}

operator fun <K, V> List<Map.Entry<K, V>>.get(key: K): V? =
    this.find { it.key == key }?.value

fun <T> sublist(list: List<T>, skipFront: Int, skipEnd: Int): List<T> {
    if (list.size < skipFront + skipEnd)
        return emptyList()
    return list.subList(skipFront, list.size - skipEnd)
}

fun <T> symmetricDifference(a: Set<T>, b: Set<T>): Set<T> {
    return (a - b) + (b - a)
}

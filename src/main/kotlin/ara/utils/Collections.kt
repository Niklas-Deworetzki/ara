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

fun <K, V> zipToMap(keys: Iterable<K>, values: Iterable<V>): MutableMap<K, V> {
    val keyIterator = keys.iterator()
    val valueIterator = values.iterator()
    val resultMap = mutableMapOf<K, V>()

    while (keyIterator.hasNext() && valueIterator.hasNext()) {
        resultMap[keyIterator.next()] = valueIterator.next()
    }

    return resultMap
}

fun <T> sublist(list: List<T>, skipFront: Int, skipEnd: Int): List<T> {
    if (list.size < skipFront + skipEnd)
        return emptyList()
    return list.subList(skipFront, list.size - skipEnd)
}

fun <T> symmetricDifference(a: Set<T>, b: Set<T>): Set<T> {
    return (a - b) + (b - a)
}

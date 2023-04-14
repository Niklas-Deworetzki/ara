package ara.utils

object Collections {

    fun <X, Y> combineWith(xs: List<X>, ys: List<Y>, action: (X, Y) -> Unit) {
        val xsIterator = xs.iterator()
        val ysIterator = ys.iterator()
        while (xsIterator.hasNext() && ysIterator.hasNext()) {
            action(xsIterator.next(), ysIterator.next())
        }
    }

    fun <T> sublist(list: List<T>, skipFront: Int, skipEnd: Int): List<T> {
        return list.subList(skipFront, list.size - skipEnd)
    }
}
package ara.interpreter

class Heap {
    private val allocated: MutableMap<Int, Value> = HashMap()
    private var lastAllocatedAddress: Int = 0

    operator fun get(address: Int): Value =
        allocated[address] ?: throw SegmentationFault(address)

    operator fun set(address: Int, value: Value) {
        allocated[address] = value
    }

    fun allocate(value: Value): Int {
        val newAddress = ++lastAllocatedAddress
        allocated[newAddress] = value
        return newAddress
    }

    fun free(address: Int): Value {
        return allocated.remove(address) ?: throw SegmentationFault(address)
    }
}

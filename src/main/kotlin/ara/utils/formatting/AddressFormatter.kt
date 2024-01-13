package ara.utils.formatting

object AddressFormatter {
    fun formatAddress(address: Int): String =
            String.format("0x%1\$08X (%1\$d)", address)
}

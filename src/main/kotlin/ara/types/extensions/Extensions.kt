package ara.types.extensions

import ara.types.Type

fun Type.isInstantiated(): Boolean = this.applyOnMaterialized(false) { true }

fun Type.getMembersType(name: String): Type? = this.applyOnMaterialized(null) {
    if (it is Type.Structure)
        it.members.find { member -> member.name == name }?.type
    else null
}

fun Type.getReferenceBase(): Type? = this.applyOnMaterialized(null) {
    if (it is Type.Reference)
        it.base
    else null
}

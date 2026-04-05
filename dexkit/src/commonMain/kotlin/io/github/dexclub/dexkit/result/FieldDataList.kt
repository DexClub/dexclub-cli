package io.github.dexclub.dexkit.result

import io.github.dexclub.dexkit.DexKitBridge
import io.github.dexclub.dexkit.query.FindField

/**
 * [FieldData] 的链式结果集，实现 [List]<[FieldData]> 并持有 bridge 引用，
 * 支持在结果范围内继续执行 findField 查询。
 */
class FieldDataList internal constructor(
    internal val bridge: DexKitBridge,
    data: List<FieldData>,
) : List<FieldData> by data {

    fun findField(query: FindField): FieldDataList =
        bridge.findField(query.copy(searchInFields = this))

    fun findField(init: FindField.() -> Unit): FieldDataList =
        findField(FindField().apply(init))
}

fun List<FieldData>.toFieldDataList(bridge: DexKitBridge): FieldDataList =
    FieldDataList(bridge, this)

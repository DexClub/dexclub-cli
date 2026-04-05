package io.github.dexclub.dexkit.result

import io.github.dexclub.dexkit.DexKitBridge
import io.github.dexclub.dexkit.query.FindMethod

/**
 * [MethodData] 的链式结果集，实现 [List]<[MethodData]> 并持有 bridge 引用，
 * 支持在结果范围内继续执行 findMethod 查询。
 */
class MethodDataList internal constructor(
    internal val bridge: DexKitBridge,
    data: List<MethodData>,
) : List<MethodData> by data {

    fun findMethod(query: FindMethod): MethodDataList =
        bridge.findMethod(query.copy(searchInMethods = this))

    fun findMethod(init: FindMethod.() -> Unit): MethodDataList =
        findMethod(FindMethod().apply(init))
}

fun List<MethodData>.toMethodDataList(bridge: DexKitBridge): MethodDataList =
    MethodDataList(bridge, this)

package io.github.dexclub.core.runtime

import io.github.dexclub.core.config.DexKitRuntimeConfig
import io.github.dexclub.dexkit.DexKitBridge

internal class DexKitRuntime(
    private val dexPaths: List<String>,
    private val config: DexKitRuntimeConfig = DexKitRuntimeConfig(),
    private val bridgeFactory: (List<String>) -> DexKitBridge = ::DexKitBridge,
) {
    private val stateLock = Any()
    private var bridge: DexKitBridge? = null

    fun getOrCreateBridge(): DexKitBridge? {
        synchronized(stateLock) {
            bridge?.let { return it }
            if (dexPaths.isEmpty()) {
                return null
            }

            return bridgeFactory(dexPaths).also { created ->
                config.threadCount?.let(created::setThreadNum)
                if (config.initFullCache) {
                    created.initFullCache()
                }
                bridge = created
            }
        }
    }

    fun readDexNum(): Int? {
        return getOrCreateBridge()?.getDexNum()
    }

    fun close() {
        val current = synchronized(stateLock) {
            val activeBridge = bridge
            bridge = null
            activeBridge
        }
        current?.close()
    }
}

package io.github.dexclub.core.impl.shared

import kotlinx.serialization.json.Json

internal val workspaceJson: Json = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = false
    explicitNulls = false
}

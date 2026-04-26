package io.github.dexclub.core.impl.resource

import io.github.dexclub.core.api.resource.DecodeXmlRequest
import io.github.dexclub.core.api.resource.DecodedXmlResult
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.impl.workspace.model.MaterialInventory

internal interface XmlExecutor {
    fun decodeXml(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        request: DecodeXmlRequest,
    ): DecodedXmlResult
}

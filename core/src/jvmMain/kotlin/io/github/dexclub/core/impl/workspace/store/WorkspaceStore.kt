package io.github.dexclub.core.impl.workspace.store

import io.github.dexclub.core.api.workspace.GcResult
import io.github.dexclub.core.impl.workspace.model.ClassSourceMapRecord
import io.github.dexclub.core.impl.workspace.model.DecodedXmlCacheRecord
import io.github.dexclub.core.impl.workspace.model.ManifestCacheRecord
import io.github.dexclub.core.impl.workspace.model.ResourceEntryIndexRecord
import io.github.dexclub.core.impl.workspace.model.ResourceTableCacheRecord
import io.github.dexclub.core.impl.workspace.model.SnapshotRecord
import io.github.dexclub.core.impl.workspace.model.TargetRecord
import io.github.dexclub.core.impl.workspace.model.WorkspaceRecord

internal interface WorkspaceStore {
    fun exists(workdir: String): Boolean

    fun dexclubDir(workdir: String): String

    fun initialize(
        workdir: String,
        workspace: WorkspaceRecord,
        target: TargetRecord,
        snapshot: SnapshotRecord,
    )

    fun loadWorkspace(workdir: String): WorkspaceRecord

    fun saveWorkspace(workdir: String, workspace: WorkspaceRecord)

    fun loadTarget(workdir: String, targetId: String): TargetRecord

    fun saveTarget(workdir: String, target: TargetRecord)

    fun loadSnapshot(workdir: String, targetId: String): SnapshotRecord?

    fun saveSnapshot(workdir: String, targetId: String, snapshot: SnapshotRecord)

    fun loadClassSourceMap(workdir: String, targetId: String): ClassSourceMapRecord?

    fun saveClassSourceMap(workdir: String, targetId: String, classSourceMap: ClassSourceMapRecord)

    fun loadManifestCache(workdir: String, targetId: String): ManifestCacheRecord?

    fun saveManifestCache(workdir: String, targetId: String, manifestCache: ManifestCacheRecord)

    fun loadResourceTableCache(workdir: String, targetId: String): ResourceTableCacheRecord?

    fun saveResourceTableCache(workdir: String, targetId: String, resourceTableCache: ResourceTableCacheRecord)

    fun loadDecodedXmlCache(workdir: String, targetId: String, xmlId: String): DecodedXmlCacheRecord?

    fun saveDecodedXmlCache(workdir: String, targetId: String, xmlId: String, decodedXmlCache: DecodedXmlCacheRecord)

    fun loadResourceEntryIndex(workdir: String, targetId: String): ResourceEntryIndexRecord?

    fun saveResourceEntryIndex(workdir: String, targetId: String, resourceEntryIndex: ResourceEntryIndexRecord)

    fun clearTargetCache(workdir: String, targetId: String): GcResult
}

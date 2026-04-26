package io.github.dexclub.core.impl.dex

import io.github.dexclub.core.api.dex.DexQueryError
import io.github.dexclub.core.api.dex.DexQueryErrorReason
import io.github.dexclub.core.impl.shared.workspaceJson
import io.github.dexclub.dexkit.query.BatchFindClassUsingStrings
import io.github.dexclub.dexkit.query.BatchFindMethodUsingStrings
import io.github.dexclub.dexkit.query.FindClass
import io.github.dexclub.dexkit.query.FindField
import io.github.dexclub.dexkit.query.FindMethod
import kotlinx.serialization.SerializationException

internal class DexQueryParser {
    fun parseFindClass(queryText: String): FindClass {
        val normalized = queryText.trim()
        if (normalized.isEmpty()) {
            throw DexQueryError(
                reason = DexQueryErrorReason.InvalidQuery,
                message = "Query JSON must not be empty",
            )
        }
        return try {
            workspaceJson.decodeFromString<FindClass>(normalized)
        } catch (cause: SerializationException) {
            throw DexQueryError(
                reason = DexQueryErrorReason.InvalidQuery,
                message = "Invalid find-class query JSON",
                cause = cause,
            )
        } catch (cause: IllegalArgumentException) {
            throw DexQueryError(
                reason = DexQueryErrorReason.InvalidQuery,
                message = "Invalid find-class query value",
                cause = cause,
            )
        }
    }

    fun parseFindMethod(queryText: String): FindMethod {
        val normalized = queryText.trim()
        if (normalized.isEmpty()) {
            throw DexQueryError(
                reason = DexQueryErrorReason.InvalidQuery,
                message = "Query JSON must not be empty",
            )
        }
        return try {
            workspaceJson.decodeFromString<FindMethod>(normalized)
        } catch (cause: SerializationException) {
            throw DexQueryError(
                reason = DexQueryErrorReason.InvalidQuery,
                message = "Invalid find-method query JSON",
                cause = cause,
            )
        } catch (cause: IllegalArgumentException) {
            throw DexQueryError(
                reason = DexQueryErrorReason.InvalidQuery,
                message = "Invalid find-method query value",
                cause = cause,
            )
        }
    }

    fun parseFindField(queryText: String): FindField {
        val normalized = queryText.trim()
        if (normalized.isEmpty()) {
            throw DexQueryError(
                reason = DexQueryErrorReason.InvalidQuery,
                message = "Query JSON must not be empty",
            )
        }
        return try {
            workspaceJson.decodeFromString<FindField>(normalized)
        } catch (cause: SerializationException) {
            throw DexQueryError(
                reason = DexQueryErrorReason.InvalidQuery,
                message = "Invalid find-field query JSON",
                cause = cause,
            )
        } catch (cause: IllegalArgumentException) {
            throw DexQueryError(
                reason = DexQueryErrorReason.InvalidQuery,
                message = "Invalid find-field query value",
                cause = cause,
            )
        }
    }

    fun parseFindClassUsingStrings(queryText: String): BatchFindClassUsingStrings {
        val normalized = queryText.trim()
        if (normalized.isEmpty()) {
            throw DexQueryError(
                reason = DexQueryErrorReason.InvalidQuery,
                message = "Query JSON must not be empty",
            )
        }
        val query = try {
            workspaceJson.decodeFromString<BatchFindClassUsingStrings>(normalized)
        } catch (cause: SerializationException) {
            throw DexQueryError(
                reason = DexQueryErrorReason.InvalidQuery,
                message = "Invalid find-class-using-strings query JSON",
                cause = cause,
            )
        } catch (cause: IllegalArgumentException) {
            throw DexQueryError(
                reason = DexQueryErrorReason.InvalidQuery,
                message = "Invalid find-class-using-strings query value",
                cause = cause,
            )
        }
        if (query.groups.isEmpty()) {
            throw DexQueryError(
                reason = DexQueryErrorReason.InvalidQuery,
                message = "find-class-using-strings query must contain at least one group",
            )
        }
        if (query.groups.values.any { it.isEmpty() }) {
            throw DexQueryError(
                reason = DexQueryErrorReason.InvalidQuery,
                message = "find-class-using-strings query groups must not be empty",
            )
        }
        return query
    }

    fun parseFindMethodUsingStrings(queryText: String): BatchFindMethodUsingStrings {
        val normalized = queryText.trim()
        if (normalized.isEmpty()) {
            throw DexQueryError(
                reason = DexQueryErrorReason.InvalidQuery,
                message = "Query JSON must not be empty",
            )
        }
        val query = try {
            workspaceJson.decodeFromString<BatchFindMethodUsingStrings>(normalized)
        } catch (cause: SerializationException) {
            throw DexQueryError(
                reason = DexQueryErrorReason.InvalidQuery,
                message = "Invalid find-method-using-strings query JSON",
                cause = cause,
            )
        } catch (cause: IllegalArgumentException) {
            throw DexQueryError(
                reason = DexQueryErrorReason.InvalidQuery,
                message = "Invalid find-method-using-strings query value",
                cause = cause,
            )
        }
        if (query.groups.isEmpty()) {
            throw DexQueryError(
                reason = DexQueryErrorReason.InvalidQuery,
                message = "find-method-using-strings query must contain at least one group",
            )
        }
        if (query.groups.values.any { it.isEmpty() }) {
            throw DexQueryError(
                reason = DexQueryErrorReason.InvalidQuery,
                message = "find-method-using-strings query groups must not be empty",
            )
        }
        return query
    }
}

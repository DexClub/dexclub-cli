package io.github.dexclub.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@PublishedApi
internal val coreJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

inline fun <reified T> T.toCoreJsonString(): String =
    coreJson.encodeToString(this)

inline fun <reified T> String.parseCoreJson(): T =
    coreJson.decodeFromString(this)

object CoreIntRangeSerializer : KSerializer<IntRange> {
    override val descriptor: SerialDescriptor = CoreIntRangeValue.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: IntRange,
    ) {
        encoder.encodeSerializableValue(
            serializer = CoreIntRangeValue.serializer(),
            value = CoreIntRangeValue(
                start = value.first,
                endInclusive = value.last,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): IntRange {
        val value = decoder.decodeSerializableValue(CoreIntRangeValue.serializer())
        return value.start..value.endInclusive
    }
}

@Serializable
private data class CoreIntRangeValue(
    val start: Int,
    val endInclusive: Int,
)

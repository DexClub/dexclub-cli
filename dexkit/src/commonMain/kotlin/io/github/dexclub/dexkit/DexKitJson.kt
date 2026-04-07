package io.github.dexclub.dexkit

import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.ClassDataList
import io.github.dexclub.dexkit.result.FieldData
import io.github.dexclub.dexkit.result.FieldDataList
import io.github.dexclub.dexkit.result.MethodData
import io.github.dexclub.dexkit.result.MethodDataList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object DexKitJson {
    val default: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
}

inline fun <reified T> T.toDexKitJsonString(json: Json = DexKitJson.default): String =
    json.encodeToString(this)

inline fun <reified T> String.parseDexKitJson(json: Json = DexKitJson.default): T =
    json.decodeFromString(this)

fun ClassDataList.toDexKitJsonString(json: Json = DexKitJson.default): String =
    json.encodeToString(ListSerializer(ClassData.serializer()), toList())

fun MethodDataList.toDexKitJsonString(json: Json = DexKitJson.default): String =
    json.encodeToString(ListSerializer(MethodData.serializer()), toList())

fun FieldDataList.toDexKitJsonString(json: Json = DexKitJson.default): String =
    json.encodeToString(ListSerializer(FieldData.serializer()), toList())

object IntRangeSerializer : KSerializer<IntRange> {
    override val descriptor: SerialDescriptor = IntRangeValue.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: IntRange,
    ) {
        encoder.encodeSerializableValue(
            serializer = IntRangeValue.serializer(),
            value = IntRangeValue(
                start = value.first,
                endInclusive = value.last,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): IntRange {
        val value = decoder.decodeSerializableValue(IntRangeValue.serializer())
        return value.start..value.endInclusive
    }
}

@Serializable
private data class IntRangeValue(
    val start: Int,
    val endInclusive: Int,
)

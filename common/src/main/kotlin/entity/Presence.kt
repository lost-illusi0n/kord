package dev.kord.common.entity

import dev.kord.common.entity.optional.Optional
import dev.kord.common.entity.optional.OptionalSnowflake
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable
data class DiscordPresenceUpdate(
        val user: DiscordPresenceUser,
        /*
        Don't trust the docs:
        2020-11-05: Discord documentation incorrectly claims this field is non-optional,
        yet it is not present during the READY event.
        */
        @SerialName("guild_id")
        val guildId: OptionalSnowflake = OptionalSnowflake.Missing,
        val status: PresenceStatus,
        val activities: List<DiscordActivity>,
        @SerialName("client_status")
        val clientStatus: DiscordClientStatus,
)

@Serializable(with = DiscordPresenceUser.Serializer::class)
data class DiscordPresenceUser(
        val id: Snowflake,
        val details: JsonObject,
) {

    internal object Serializer : KSerializer<DiscordPresenceUser> {

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Kord.DiscordPresenceUser") {
            element<Snowflake>("id")
            element<JsonElement>("details")
        }

        override fun deserialize(decoder: Decoder): DiscordPresenceUser {
            val jsonDecoder = decoder as? JsonDecoder ?: error("Can be deserialized only by JSON")
            val json = jsonDecoder.decodeJsonElement().jsonObject
            val id = Snowflake(json.getValue("id").jsonPrimitive.content)
            val details = json.toMutableMap()
            details.remove("id")

            return DiscordPresenceUser(id, JsonObject(details))
        }

        override fun serialize(encoder: Encoder, value: DiscordPresenceUser) {
            val jsonEncoder = encoder as? JsonEncoder ?: error("Can be serialized only by JSON")
            val details = value.details.toMutableMap()
            details["id"] = JsonPrimitive(value.id.asString)

            jsonEncoder.encodeJsonElement(JsonObject(details))
        }
    }

}

@Serializable
data class DiscordClientStatus(
        val desktop: Optional<PresenceStatus> = Optional.Missing(),
        val mobile: Optional<PresenceStatus> = Optional.Missing(),
        val web: Optional<PresenceStatus> = Optional.Missing(),
)

@Serializable(with = PresenceStatus.StatusSerializer::class)
sealed class PresenceStatus(val value: String) {

    class Unknown(value: String) : PresenceStatus(value)
    object Online : PresenceStatus("online")
    object Idle : PresenceStatus("idle")
    object DoNotDisturb : PresenceStatus("dnd")
    object Offline : PresenceStatus("offline")
    object Invisible : PresenceStatus("invisible")

    companion object StatusSerializer : KSerializer<PresenceStatus> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Kord.ClientStatus", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): PresenceStatus = when (val value = decoder.decodeString()) {
            "online" -> Online
            "idle" -> Idle
            "dnd" -> DoNotDisturb
            "offline" -> Offline
            "invisible" -> Invisible
            else -> Unknown(value)
        }

        override fun serialize(encoder: Encoder, value: PresenceStatus) {
            encoder.encodeString(value.value)
        }
    }
}
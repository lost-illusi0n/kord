package dev.kord.voice

import dev.kord.common.entity.Snowflake
import dev.kord.gateway.Gateway
import dev.kord.gateway.UpdateVoiceStatus
import dev.kord.voice.gateway.VoiceGateway
import dev.kord.voice.gateway.VoiceGatewayConfiguration
import dev.kord.voice.handlers.UdpLifeCycleHandler
import dev.kord.voice.handlers.VoiceUpdateEventHandler
import dev.kord.voice.udp.AudioFramePoller
import dev.kord.voice.udp.AudioFramePollerConfiguration
import dev.kord.voice.udp.AudioFramePollerConfigurationBuilder
import kotlinx.coroutines.*
import mu.KotlinLogging
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeSource

/**
 * Data that represents a [VoiceConnection], these will never change during the life time of a [VoiceConnection].
 *
 * @param selfId the id of the bot connecting to a voice channel.
 * @param guildId the id of the guild that the bot is connecting to.
 */
data class VoiceConnectionData(
    val selfId: Snowflake,
    val guildId: Snowflake,
)

private val voiceConnectionLogger = KotlinLogging.logger { }

/**
 * A [VoiceConnection] is an adapter that forms the concept of a voice connection, or a connection between you and Discord voice servers.
 *
 * @param gateway the [Gateway] that handles events for the guild this [VoiceConnection] represents.
 * @param voiceGateway the underlying [VoiceGateway] for this voice connection.
 * @param voiceGatewayConfiguration the configuration used for [voiceGateway].
 * @param data the data representing this [VoiceConnection].
 * @param audioProvider a [AudioProvider] that will provide [AudioFrame] when required.
 * @param frameInterceptorFactory a factory for [FrameInterceptor]s that is used whenever audio is ready to be sent. See [FrameInterceptor] and [DefaultFrameInterceptor].
 * @param voiceDispatcher the dispatcher used for this voice connection.
 */
class VoiceConnection(
    val gateway: Gateway,
    val voiceGateway: VoiceGateway,
    internal var voiceGatewayConfiguration: VoiceGatewayConfiguration,
    internal val data: VoiceConnectionData,
    var audioProvider: AudioProvider,
    var frameInterceptorFactory: (FrameInterceptorContext) -> FrameInterceptor,
    voiceDispatcher: CoroutineDispatcher
) : CoroutineScope {
    override val coroutineContext: CoroutineContext =
        SupervisorJob() + voiceDispatcher + CoroutineName("Voice Connection for Guild ${data.guildId.value}")

    private val audioFramePoller = AudioFramePoller(TimeSource.Monotonic, voiceDispatcher)

    init {
        // handle voice state/server updates (e.g., a move, disconnect, voice server change, etc.)
        VoiceUpdateEventHandler(this, gateway.events)

        // handle the lifecycle of the udp connection
        UdpLifeCycleHandler(
            voiceGateway.events,
            voiceGateway::send,
            { audioFramePoller.start(it.withConnection(this)) },
            voiceDispatcher
        )
    }

    /**
     * Logs into the voice gateway, and begins the process of an audio-ready voice session.
     */
    fun connect() {
        launch {
            voiceGateway.start(voiceGatewayConfiguration)
        }
    }

    /**
     * Disconnects from the voice gateway, does not change the voice state.
     */
    suspend fun disconnect() {
        voiceGateway.stop()
    }

    /**
     * Disconnects from Discord voice servers, and leaves the voice channel.
     */
    suspend fun leave() {
        disconnect()

        gateway.send(
            UpdateVoiceStatus(
                guildId = data.guildId,
                channelId = null,
                selfMute = false,
                selfDeaf = false
            )
        )
    }
}

private fun AudioFramePollerConfigurationBuilder.withConnection(connection: VoiceConnection): AudioFramePollerConfiguration {
    this.provider = connection.audioProvider
    this.interceptorFactory = connection.frameInterceptorFactory

    this.baseFrameInterceptorContext = FrameInterceptorContextBuilder().apply {
        gateway = connection.gateway
        voiceGateway = connection.voiceGateway
    }

    return build()
}
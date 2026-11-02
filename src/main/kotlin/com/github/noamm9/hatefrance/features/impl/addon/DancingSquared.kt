package com.github.noamm9.hatefrance.features.impl.addon

import com.github.noamm9.NoammAddons
import com.github.noamm9.event.impl.MainThreadPacketReceivedEvent
import com.github.noamm9.event.impl.TickEvent
import com.github.noamm9.event.impl.WorldChangeEvent
import com.github.noamm9.features.Feature
import com.github.noamm9.utils.ChatUtils
import com.github.noamm9.utils.ChatUtils.unformattedText
import com.github.noamm9.utils.MathUtils
import com.github.noamm9.utils.PlayerUtils
import com.github.noamm9.utils.location.LocationUtils
import com.github.noamm9.utils.location.WorldType
import kotlinx.coroutines.*
import net.minecraft.client.KeyMapping
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

object DancingSquared : Feature("Automatically completes the Dance Room puzzle") {
    private val debug get() = NoammAddons.debugFlags.contains("dancing")

    private val pitchList = hashSetOf(0.52380955f, 1.0476191f, 0.6984127f, 0.8888889f)
    private const val COMPLETE_PITCH = 0.74603176f
    private const val JUMP_DELAY = 500L
    private const val PUNCH_DELAY = 800L

    private var beats = 0
    private var isActive = false
    private var inMirrorverse = false
    private var seg = 0
    private var currentKeyBind: KeyMapping? = null
    private val danceScope by lazy { CoroutineScope(SupervisorJob() + mc.asCoroutineDispatcher()) }

    private val segments by lazy {
        val o = mc.options
        listOf(
            Segment("z", -105.0, o.keyLeft),
            Segment("x", -262.0, o.keyDown),
            Segment("z", -107.0, o.keyRight),
            Segment("x", -264.0, o.keyUp),
        )
    }

    override fun init() {
        register<TickEvent.Start> {
            val player = mc.player
            val wasInMirrorverse = inMirrorverse
            inMirrorverse = LocationUtils.world == WorldType.Rift && player != null &&
                    player.y >= 32 && player.y < 36 && player.x in -266.0..-260.0 && player.z in -109.0..-103.0
            if (!inMirrorverse) setInactive()

            if (inMirrorverse != wasInMirrorverse && debug) {
                ChatUtils.chat("[DancingSquared] Mirrorverse status changed: $inMirrorverse") // TODO: make this debug
            }
        }

        register<MainThreadPacketReceivedEvent.Post> {
            val packet = event.packet as? ClientboundSetSubtitleTextPacket ?: return@register
            if (!inMirrorverse && !debug) return@register
            val text = packet.text.unformattedText
            if (debug) ChatUtils.chat("[DancingSquared] Subtitle received: $text (InMirrorverse: $inMirrorverse)")

            if (inMirrorverse && !isActive && mc.gui.screen() == null && mc.isWindowActive && text.contains("Move!")) {
                ChatUtils.modMessage("&bDance Room &7» &aEnabled via Subtitle!")
                setActive()
            }
        }

        register<TickEvent.Start> {
            if (!isActive) return@register
            val player = mc.player ?: return@register
            val key = currentKeyBind ?: return@register

            val segment = segments[seg]
            val playerPos = if (segment.axis == "x") player.x else player.z
            if (playerPos !in segment.target - 1.0..segment.target) return@register

            key.isDown = false
            currentKeyBind = null
            seg = (seg + 1) % segments.size
        }

        register<MainThreadPacketReceivedEvent.Post> {
            val packet = event.packet as? ClientboundSoundPacket ?: return@register
            if (!inMirrorverse || !isActive) return@register
            val name = packet.sound.value().location.toString()

            if (name.contains("burp") || name.contains("villager.no")) {
                ChatUtils.modMessage("&bDance Room &7» &cFailed!")
                setInactive()
                return@register
            }

            if (name.contains("note_block.bass")) {
                val isBeat = pitchList.any { abs(it - packet.pitch) < 0.01 }
                val isComplete = abs(COMPLETE_PITCH - packet.pitch) < 0.01

                if (isBeat) {
                    beats++
                    if (debug) ChatUtils.chat("[DancingSquared] Total beats: $beats")
                    doMove(beats)
                } else if (isComplete) {
                    ChatUtils.modMessage("&bDance Room &7» &aCompleted!")
                    setInactive()
                }
            }
        }

        register<WorldChangeEvent> {
            setInactive()
            inMirrorverse = false
        }
    }

    override fun onDisable() {
        setInactive()
        inMirrorverse = false
        super.onDisable()
    }

    private fun setActive() {
        isActive = true
        beats = 0
        seg = 0

        danceScope.launch {
            PlayerUtils.rotateSmoothly(MathUtils.Rotation(90f, 90f), 200)
        }
    }

    private fun setInactive() {
        if (!isActive) return
        isActive = false
        danceScope.coroutineContext.cancelChildren()
        segments.forEach { it.key.isDown = false }
        mc.options.keyShift.isDown = false
        mc.options.keyJump.isDown = false
        currentKeyBind = null
        beats = 0
        seg = 0
    }

    private fun doMove(beat: Int) {
        val status = mutableListOf<String>()
        val beat = beat - 1

        if (beat == 0 || beat % 2 == 1) {
            val nextKey = segments[seg].key
            if (debug) ChatUtils.chat("[DancingSquared] Beat $beat: Switching to Segment $seg (Key: ${nextKey.name})")
            status.add("moving")
            currentKeyBind?.isDown = false
            nextKey.isDown = true
            currentKeyBind = nextKey
        }

        if (beat >= 8) {
            if (beat % 4 == 0) {
                status.add("sneaking")
                mc.options.keyShift.isDown = true
            } else if (beat % 4 == 1) {
                status.add("unsneaking")
                mc.options.keyShift.isDown = false
            }
        }

        if (beat >= 24 && (beat % 8 == 0 || beat % 8 == 2)) {
            status.add("jumping")
            danceScope.launch {
                delay(JUMP_DELAY.milliseconds)
                mc.options.keyJump.isDown = true
                delay(100.milliseconds)
                mc.options.keyJump.isDown = false
            }
        }

        if (beat >= 64 && beat % 2 == 0) {
            status.add("punching")
            danceScope.launch {
                delay(PUNCH_DELAY.milliseconds)
                PlayerUtils.leftClick()
            }
        }

        val statusStr = if (status.isEmpty()) "" else ": ${status.joinToString(", ")}"
        ChatUtils.modMessage("&7Beat $beat$statusStr.")
    }

    private class Segment(val axis: String, val target: Double, val key: KeyMapping)
}

package com.serebit.wraith.core

interface LedComponent {
    @UseExperimental(ExperimentalUnsignedTypes::class)
    val values: UByteArray
    var color: Color
    var speed: Int
    var brightness: Int

    fun assignValuesFromChannel(channelValues: ChannelValues)
}

interface BasicLedComponent : LedComponent {
    @UseExperimental(ExperimentalUnsignedTypes::class)
    val channel: UByte
    var mode: LedMode

    @UseExperimental(ExperimentalUnsignedTypes::class)
    override fun assignValuesFromChannel(channelValues: ChannelValues) {
        mode = LedMode.values().first { it.mode == channelValues.mode }
        if (mode != LedMode.CYCLE) {
            color = channelValues.color
        }
        speed = mode.speeds.indexOfOrNull(channelValues.speed)?.plus(1) ?: 3
        brightness = mode.brightnesses.indexOfOrNull(channelValues.brightness)?.plus(1) ?: 2
    }

    @UseExperimental(ExperimentalUnsignedTypes::class)
    override val values: UByteArray
        get() {
            val brightness = mode.brightnesses.elementAtOrNull(brightness - 1) ?: 0u
            val speed = mode.speeds.elementAtOrNull(speed - 1) ?: 0x2Cu
            return ubyteArrayOf(channel, speed, 0x20u, mode.mode, 0xFFu, brightness, *color.bytes)
        }
}

class LogoComponent(initialValues: ChannelValues) : BasicLedComponent {
    @UseExperimental(ExperimentalUnsignedTypes::class)
    override val channel: UByte = 0x05u
    override lateinit var mode: LedMode
    override var color = Color(0, 0, 0)
    override var speed = 0
    override var brightness = 0

    init {
        assignValuesFromChannel(initialValues)
    }
}

class FanComponent(initialValues: ChannelValues) : BasicLedComponent {
    @UseExperimental(ExperimentalUnsignedTypes::class)
    override val channel: UByte = 0x06u
    override lateinit var mode: LedMode
    override var color = Color(0, 0, 0)
    override var speed = 0
    override var brightness = 0
    var mirage = false

    init {
        assignValuesFromChannel(initialValues)
    }
}

@UseExperimental(ExperimentalUnsignedTypes::class)
enum class RotationDirection(val value: UByte) { CLOCKWISE(0u), COUNTERCLOCKWISE(1u); }

@UseExperimental(ExperimentalUnsignedTypes::class)
class RingComponent(initialValues: ChannelValues) : LedComponent {
    lateinit var mode: RingMode
    override lateinit var color: Color
    override var speed = 0
    override var brightness = 0
    lateinit var direction: RotationDirection

    init {
        assignValuesFromChannel(initialValues)
    }

    override fun assignValuesFromChannel(channelValues: ChannelValues) {
        mode = RingMode.values().first { it.channel == channelValues.channel && it.mode == channelValues.mode }
        color = channelValues.let { if (mode.supportsColor) it.color else Color(0u, 0u, 0u) }
        speed = mode.speeds.indexOfOrNull(channelValues.speed)?.plus(1) ?: 3
        brightness = mode.brightnesses.indexOfOrNull(channelValues.brightness)?.plus(1) ?: 2
        direction = if (mode.supportsDirection) {
            RotationDirection.values()[channelValues.colorSource.toInt()]
        } else {
            RotationDirection.CLOCKWISE
        }
    }

    override val values: UByteArray
        get() {
            val brightness = mode.brightnesses.elementAtOrNull(brightness - 1) ?: 0x99u
            val speed = mode.speeds.elementAtOrNull(speed - 1) ?: 0xFFu
            val colorSource = if (mode.supportsDirection) direction.value else mode.colorSource
            return ubyteArrayOf(mode.channel, speed, colorSource, mode.mode, 0xFFu, brightness, *color.bytes)
        }
}

@UseExperimental(ExperimentalUnsignedTypes::class)
private fun UByteArray.indexOfOrNull(value: UByte) = indexOf(value).let { if (it == -1) null else it }

@UseExperimental(ExperimentalUnsignedTypes::class)
enum class LedMode(
    val mode: UByte,
    val brightnesses: UByteArray = ubyteArrayOf(0x4Cu, 0x99u, 0xFFu),
    val speeds: UByteArray = ubyteArrayOf(),
    val supportsColor: Boolean = false
) {
    OFF(0x00u, ubyteArrayOf()),
    STATIC(0x01u, supportsColor = true),
    CYCLE(0x02u, ubyteArrayOf(0x10u, 0x40u, 0x7Fu), ubyteArrayOf(0x96u, 0x8Cu, 0x80u, 0x6Eu, 0x68u)),
    BREATHE(0x03u, speeds = ubyteArrayOf(0x3Cu, 0x37u, 0x31u, 0x2Cu, 0x26u), supportsColor = true)
}

@UseExperimental(ExperimentalUnsignedTypes::class)
enum class RingMode(
    val channel: UByte, val mode: UByte,
    val brightnesses: UByteArray = ubyteArrayOf(0x4Cu, 0x99u, 0xFFu),
    val speeds: UByteArray = ubyteArrayOf(),
    val supportsColor: Boolean = false,
    val supportsDirection: Boolean = false,
    val colorSource: UByte = 0x20u
) {
    OFF(0xFEu, 0xFFu, ubyteArrayOf(), ubyteArrayOf()),
    STATIC(0x00u, 0xFFu, supportsColor = true),
    RAINBOW(0x07u, 0x05u, speeds = ubyteArrayOf(0x72u, 0x68u, 0x64u, 0x62u, 0x61u), colorSource = 0u),
    SWIRL(
        0x0Au, 0x4Au, speeds = ubyteArrayOf(0x77u, 0x74u, 0x6Eu, 0x6Bu, 0x67u),
        supportsColor = true, supportsDirection = true
    ),
    CHASE(
        0x09u, 0xC3u, speeds = ubyteArrayOf(0x77u, 0x74u, 0x6Eu, 0x6Bu, 0x67u),
        supportsColor = true, supportsDirection = true
    ),
    BOUNCE(0x08u, 0xFFu, speeds = ubyteArrayOf(0x77u, 0x74u, 0x6Eu, 0x6Bu, 0x67u), colorSource = 0x80u),
    MORSE(0x0Bu, 0x05u, supportsColor = true, colorSource = 0u),
    CYCLE(0x02u, 0xFFu, ubyteArrayOf(0x10u, 0x40u, 0x7Fu), ubyteArrayOf(0x96u, 0x8Cu, 0x80u, 0x6Eu, 0x68u)),
    BREATHE(0x01u, 0xFFu, speeds = ubyteArrayOf(0x3Cu, 0x37u, 0x31u, 0x2Cu, 0x26u), supportsColor = true)
}

class Color(val r: Int, val g: Int, val b: Int) {
    @UseExperimental(ExperimentalUnsignedTypes::class)
    constructor(r: UByte, g: UByte, b: UByte) : this(r.toInt(), g.toInt(), b.toInt())

    constructor(r: Double, g: Double, b: Double) : this((255 * r).toInt(), (255 * g).toInt(), (255 * b).toInt())
}

@UseExperimental(ExperimentalUnsignedTypes::class)
val Color.bytes
    get() = ubyteArrayOf(r.toUByte(), g.toUByte(), b.toUByte())

val LedMode.supportsBrightness get() = brightnesses.isNotEmpty()
val LedMode.supportsSpeed get() = speeds.isNotEmpty()

val RingMode.supportsBrightness get() = brightnesses.isNotEmpty()
val RingMode.supportsSpeed get() = speeds.isNotEmpty()

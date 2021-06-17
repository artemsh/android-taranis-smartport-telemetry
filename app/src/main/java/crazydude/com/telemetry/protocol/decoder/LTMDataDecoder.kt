package crazydude.com.telemetry.protocol.decoder

import android.util.Log
import crazydude.com.telemetry.protocol.Protocol
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.DecimalFormat
import kotlin.math.round

class LTMDataDecoder(listener: Listener) : DataDecoder(listener) {

    private var batCell = 0
    private var drawnTimer: Long = 0
    private var previousDrawn = 0

    override fun decodeData(data: Protocol.Companion.TelemetryData) {
        var decoded = true
        val byteBuffer = ByteBuffer.wrap(data.rawData).order(ByteOrder.LITTLE_ENDIAN)
        when (data.telemetryType) {
            Protocol.GPS -> {
                val latitude = byteBuffer.int / 10000000.toDouble()
                val longitude = byteBuffer.int / 10000000.toDouble()
                Log.d("LTMDecoder", "GPS: $latitude, $longitude")
                val speed = byteBuffer.get()
                val altitude = byteBuffer.int
                val gpsState = byteBuffer.get()
                listener.onGPSState(((gpsState.toUInt() shr 2) and 0xFF.toUInt()).toInt(), ((gpsState.toUInt() shr 0) and 1.toUInt()) == 1.toUInt())
                listener.onGPSData(latitude, longitude)
                listener.onGSpeedData(speed.toUByte().toByte() * (18 / 5f))
                listener.onAltitudeData(altitude / 100f)
            }

            Protocol.ATTITUDE -> {
                val pitch = byteBuffer.short
                val roll = byteBuffer.short
                val heading = byteBuffer.short
                listener.onRollData(roll.toFloat())
                listener.onPitchData(pitch.toFloat())
                listener.onHeadingData(heading.toFloat())
            }

            Protocol.FLYMODE -> {
                val status = byteBuffer.get()
                listener.onFlyModeData(((status.toUInt() shr 0) and 1.toUInt()) == 1.toUInt(), false, null, null)
            }

            Protocol.FUEL -> {
                val drawn = byteBuffer.short.toInt()
                if (previousDrawn == 0)
                    previousDrawn = drawn
                if (drawnTimer == 0L)
                    drawnTimer = System.currentTimeMillis()
                val currentDrawn = drawn - previousDrawn
                val currentTimer: Long = System.currentTimeMillis()
                val currentDrawnTimeout = (currentTimer - drawnTimer).toInt()
                if (currentDrawn > 0) {
                    drawnTimer = currentTimer
                    previousDrawn = drawn
                    listener.onCurrentData((((currentDrawn * 3600f * (currentDrawnTimeout / 1000f)) / currentDrawnTimeout) / (currentDrawnTimeout / 1000f))
                        .toBigDecimal().setScale(2, RoundingMode.HALF_EVEN).toFloat())
                }
                listener.onFuelData(drawn)
            }

            Protocol.VBAT -> {
                val battery = byteBuffer.short / 1000f
                if (batCell == 0 && battery > 3.8) {
                    batCell = (battery / 3.8).toInt()
                    if (battery / batCell > 4.2)
                        ++batCell
                    if (battery / batCell < 2.8)
                        --batCell
                }
                if(batCell != 0)
                    listener.onCellVoltageData(battery / batCell)
                listener.onVBATData(battery)
            }

            else -> {
                decoded = false
            }
        }

        if (decoded) {
            listener.onSuccessDecode()
        }
    }
}
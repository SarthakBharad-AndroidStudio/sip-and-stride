package com.example.sipandstride

import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One recorded walk.
 *
 * It implements Serializable so that a whole object can be put into an Intent with
 * putExtra() and read back with getSerializableExtra() — the technique from Tutorial 3,
 * exercise 3. Without Serializable, Android would only let us pass single values
 * (Int, String, ...) and we would need eight extras instead of one.
 */
data class WalkSession(
    val id: Long,
    val steps: Int,
    val meters: Int,
    val dateMillis: Long,
    val startAddress: String,
    val endAddress: String,
    val startLat: Double,
    val startLon: Double
) : Serializable {

    /** "23.09.2026 14:05" — a timestamp a human can read. */
    fun formattedDate(): String {
        val format = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        return format.format(Date(dateMillis))
    }

    /** True if we actually got a GPS fix; 0.0 / 0.0 is our "no position" marker. */
    fun hasLocation(): Boolean = startLat != 0.0 || startLon != 0.0

    /**
     * Turns the object into one text line so it can be stored in SharedPreferences.
     * SharedPreferences only knows simple types (String, Int, Boolean, ...), so a list of
     * objects has to be converted into text first. The fields are separated by "|" and any
     * "|" a street name might contain is replaced, otherwise reading it back would break.
     */
    fun toLine(): String {
        val safeStart = startAddress.replace("|", " ").replace("\n", " ")
        val safeEnd = endAddress.replace("|", " ").replace("\n", " ")

        return listOf(id, steps, meters, dateMillis, safeStart, safeEnd, startLat, startLon)
            .joinToString("|")
    }

    companion object {

        /** The opposite of toLine(). Returns null for a damaged line instead of crashing. */
        fun fromLine(line: String): WalkSession? {
            val parts = line.split("|")
            if (parts.size != 8) return null

            return try {
                WalkSession(
                    id = parts[0].toLong(),
                    steps = parts[1].toInt(),
                    meters = parts[2].toInt(),
                    dateMillis = parts[3].toLong(),
                    startAddress = parts[4],
                    endAddress = parts[5],
                    startLat = parts[6].toDouble(),
                    startLon = parts[7].toDouble()
                )
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}
package com.toyrobotworkshop.auspex.util

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Simple in-app diagnostic event logger.
 * Captures key events during camera initialization so the user can see what's happening.
 * Uses Compose's mutableStateListOf for automatic recomposition when events are added.
 */
object DiagnosticLogger {

    data class DiagnosticEvent(
        val timestamp: String,
        val category: String,
        val message: String,
    )

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    // Compose-observable list — triggers recomposition on add/remove
    val events = mutableStateListOf<DiagnosticEvent>()

    fun log(category: String, message: String) {
        events.add(DiagnosticEvent(
            timestamp = dateFormat.format(Date()),
            category = category,
            message = message,
        ))
    }

    fun clear() {
        events.clear()
    }

    // Convenience loggers for common categories
    fun init(msg: String) = log("INIT", msg)
    fun usb(msg: String) = log("USB", msg)
    fun perm(msg: String) = log("PERM", msg)
    fun error(msg: String) = log("ERROR", msg)
    fun camera(msg: String) = log("CAMERA", msg)
}

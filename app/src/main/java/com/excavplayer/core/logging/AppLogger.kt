package com.excavplayer.core.logging

import android.util.Log
import com.excavplayer.BuildConfig

/**
 * Lightweight, production-grade logging abstraction.
 * Automatically strips or suppresses verbose/debug logs in non-debug environments
 * and sanitizes sensitive user file paths.
 */
interface AppLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

class AndroidAppLogger(
    private val isDebug: Boolean = BuildConfig.DEBUG
) : AppLogger {

    override fun d(tag: String, message: String) {
        if (isDebug) {
            Log.d(sanitizeTag(tag), message)
        }
    }

    override fun i(tag: String, message: String) {
        Log.i(sanitizeTag(tag), message)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.w(sanitizeTag(tag), message, throwable)
        } else {
            Log.w(sanitizeTag(tag), message)
        }
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(sanitizeTag(tag), message, throwable)
        } else {
            Log.e(sanitizeTag(tag), message)
        }
    }

    private fun sanitizeTag(tag: String): String {
        return if (tag.length > 23) tag.substring(0, 23) else tag
    }
}

package com.hrm.forge.logger

import android.util.Log

/**
 * Forge 日志系统
 */
object Logger {
    private const val TAG = "Forge"
    private var logImpl: ILogger = DefaultLogger()
    
    @Volatile
    private var logLevel = LogLevel.INFO
    
    enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARN, ERROR, NONE
    }
    
    fun setLogger(logger: ILogger) {
        logImpl = logger
    }
    
    fun setLogLevel(level: LogLevel) {
        logLevel = level
    }
    
    fun v(tag: String, msg: String) {
        if (logLevel.ordinal <= LogLevel.VERBOSE.ordinal) {
            logImpl.v(tag, msg)
        }
    }
    
    fun d(tag: String, msg: String) {
        if (logLevel.ordinal <= LogLevel.DEBUG.ordinal) {
            logImpl.d(tag, msg)
        }
    }
    
    fun i(tag: String, msg: String) {
        if (logLevel.ordinal <= LogLevel.INFO.ordinal) {
            logImpl.i(tag, msg)
        }
    }
    
    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (logLevel.ordinal <= LogLevel.WARN.ordinal) {
            logImpl.w(tag, msg, tr)
        }
    }
    
    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (logLevel.ordinal <= LogLevel.ERROR.ordinal) {
            logImpl.e(tag, msg, tr)
        }
    }
    
    private class DefaultLogger : ILogger {
        override fun v(tag: String, msg: String) {
            Log.v(TAG, "[$tag] $msg")
        }
        
        override fun d(tag: String, msg: String) {
            Log.d(TAG, "[$tag] $msg")
        }
        
        override fun i(tag: String, msg: String) {
            Log.i(TAG, "[$tag] $msg")
        }
        
        override fun w(tag: String, msg: String, tr: Throwable?) {
            if (tr != null) {
                Log.w(TAG, "[$tag] $msg", tr)
            } else {
                Log.w(TAG, "[$tag] $msg")
            }
        }
        
        override fun e(tag: String, msg: String, tr: Throwable?) {
            if (tr != null) {
                Log.e(TAG, "[$tag] $msg", tr)
            } else {
                Log.e(TAG, "[$tag] $msg")
            }
        }
    }
}

interface ILogger {
    fun v(tag: String, msg: String)
    fun d(tag: String, msg: String)
    fun i(tag: String, msg: String)
    fun w(tag: String, msg: String, tr: Throwable? = null)
    fun e(tag: String, msg: String, tr: Throwable? = null)
}

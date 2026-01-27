package com.hrm.forge.internal.util

import android.content.Context
import android.content.SharedPreferences
import com.hrm.forge.internal.log.Logger
import com.hrm.forge.internal.util.DataStorage.init

/**
 * 数据存储工具类
 *
 * 使用前必须调用 [init] 方法进行初始化
 */
internal object DataStorage {
    private const val TAG = "DataSavingUtils"
    private const val PREFS_NAME = "forge_prefs"

    @Volatile
    private var prefs: SharedPreferences? = null

    /**
     * 初始化 SharedPreferences
     *
     * @param context 应用上下文（可以是 Application 或 Activity）
     */
    fun init(context: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    // 注意：在 attachBaseContext 阶段，applicationContext 可能为 null
                    // 所以直接使用传入的 context
                    prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                }
            }
        }
    }

    /**
     * 检查是否已初始化
     */
    private fun ensureInitialized(): Boolean {
        if (prefs == null) {
            Logger.e(TAG, "DataStorage not initialized! Please call init() first.")
            return false
        }
        return true
    }

    fun putString(key: String, value: String?): Boolean {
        if (!ensureInitialized()) return false
        try {
            prefs?.edit()?.putString(key, value)?.apply()
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "Put string failed: $key", e)
            return false
        }
    }

    fun getString(key: String, defaultValue: String? = null): String? {
        if (!ensureInitialized()) return defaultValue
        return try {
            prefs?.getString(key, defaultValue)
        } catch (e: Exception) {
            Logger.e(TAG, "Get string failed: $key", e)
            defaultValue
        }
    }

    fun putInt(key: String, value: Int) {
        if (!ensureInitialized()) return
        try {
            prefs?.edit()?.putInt(key, value)?.apply()
        } catch (e: Exception) {
            Logger.e(TAG, "Put int failed: $key", e)
        }
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        if (!ensureInitialized()) return defaultValue
        return try {
            prefs?.getInt(key, defaultValue) ?: defaultValue
        } catch (e: Exception) {
            Logger.e(TAG, "Get int failed: $key", e)
            defaultValue
        }
    }

    fun putLong(key: String, value: Long): Boolean {
        if (!ensureInitialized()) return false
        try {
            prefs?.edit()?.putLong(key, value)?.apply()
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "Put long failed: $key", e)
            return false
        }
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        if (!ensureInitialized()) return defaultValue
        return try {
            prefs?.getLong(key, defaultValue) ?: defaultValue
        } catch (e: Exception) {
            Logger.e(TAG, "Get long failed: $key", e)
            defaultValue
        }
    }

    fun putBoolean(key: String, value: Boolean): Boolean {
        if (!ensureInitialized()) return false
        try {
            prefs?.edit()?.putBoolean(key, value)?.apply()
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "Put boolean failed: $key", e)
            return false
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        if (!ensureInitialized()) return defaultValue
        return try {
            prefs?.getBoolean(key, defaultValue) ?: defaultValue
        } catch (e: Exception) {
            Logger.e(TAG, "Get boolean failed: $key", e)
            defaultValue
        }
    }

    fun remove(key: String) {
        if (!ensureInitialized()) return
        try {
            prefs?.edit()?.remove(key)?.apply()
        } catch (e: Exception) {
            Logger.e(TAG, "Remove failed: $key", e)
        }
    }

    fun clear() {
        if (!ensureInitialized()) return
        try {
            prefs?.edit()?.clear()?.apply()
        } catch (e: Exception) {
            Logger.e(TAG, "Clear failed", e)
        }
    }

    fun contains(key: String): Boolean {
        if (!ensureInitialized()) return false
        return try {
            prefs?.contains(key) ?: false
        } catch (e: Exception) {
            Logger.e(TAG, "Contains check failed: $key", e)
            false
        }
    }
}

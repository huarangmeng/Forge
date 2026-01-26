package com.hrm.forge.demo

import android.content.Context
import android.util.Log

/**
 * ApplicationLike
 * 
 * 这是实际的应用逻辑类，所有的初始化和业务逻辑都应该写在这里
 */
class DemoApplicationLike(private val context: Context) {
    
    companion object {
        private const val TAG = "DemoApplicationLike"
    }
    
    /**
     * 对应 Application.attachBaseContext()
     * 在这里进行早期初始化
     */
    fun attachBaseContext(base: Context) {
        Log.i(TAG, "attachBaseContext called")
        
        // 早期初始化代码
        // 例如：MultiDex.install(base)
    }
    
    /**
     * 对应 Application.onCreate()
     * 在这里进行应用初始化
     */
    fun onCreate() {
        Log.i(TAG, "onCreate called")
        
        // 应用初始化代码
        // 例如：初始化第三方 SDK
        Log.i(TAG, "Demo app initialized successfully!")
    }
    
    /**
     * 对应 Application.onTerminate()
     */
    fun onTerminate() {
        Log.i(TAG, "onTerminate called")
        
        // 清理代码
    }
    
    /**
     * 对应 Application.onLowMemory()
     */
    fun onLowMemory() {
        Log.i(TAG, "onLowMemory called")
        
        // 低内存处理
        // 例如：清理缓存
    }
    
    /**
     * 对应 Application.onTrimMemory()
     */
    fun onTrimMemory(level: Int) {
        Log.i(TAG, "onTrimMemory called: level=$level")
        
        // 内存修剪处理
        when (level) {
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                Log.d(TAG, "Device running moderately slow")
            }
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                Log.d(TAG, "Device running very slow")
            }
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.d(TAG, "Device running critically slow")
            }
        }
    }
}

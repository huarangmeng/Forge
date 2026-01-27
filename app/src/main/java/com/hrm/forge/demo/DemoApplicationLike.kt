package com.hrm.forge.demo

import android.content.Context
import android.util.Log

/**
 * ApplicationLike
 * 
 * ✅ 这是实际的应用逻辑类，所有的初始化和业务逻辑都应该写在这里
 * 
 * 重要说明：
 * - ✅ 这个类的所有代码都会被热更新
 * - ✅ 这个类引用的所有业务类也会被热更新
 * - ✅ 适合放置：SDK 初始化、业务逻辑初始化、数据库初始化等
 * 
 * 代码生效区间：
 * - 此类在 ForgeApplicationDelegate.attachBaseContext() 后首次加载
 * - 由新的 ClassLoader 加载，因此可以被热更新
 */
class DemoApplicationLike(private val context: Context) {
    
    companion object {
        private const val TAG = "DemoApplicationLike"
        
        // ✅ 静态字段也会被热更新（因为类本身会被重新加载）
        var initTime: Long = 0
        var updateCount: Int = 0
    }
    
    /**
     * 对应 Application.attachBaseContext()
     * 
     * ✅ 这里的代码会被热更新
     * 适合进行早期初始化，比如：
     * - MultiDex.install()
     * - 崩溃监控 SDK 初始化
     * - 日志框架初始化
     */
    fun attachBaseContext(base: Context) {
        Log.i(TAG, "✅ attachBaseContext called (CAN be hot updated)")
        
        // ✅ 示例：模拟早期初始化
        initTime = System.currentTimeMillis()
        updateCount++
        
        Log.i(TAG, "Update count: $updateCount")
    }
    
    /**
     * 对应 Application.onCreate()
     * 
     * ✅ 这里的代码会被热更新
     * 适合进行应用初始化，比如：
     * - 第三方 SDK 初始化（如网络库、图片库）
     * - 数据库初始化
     * - 全局单例初始化
     * - 业务模块初始化
     */
    fun onCreate() {
        Log.i(TAG, "✅ onCreate called (CAN be hot updated)")
        
        // ✅ 示例：模拟业务初始化
        initSDKs()
        initManagers()
        
        Log.i(TAG, "✅ Demo app initialized successfully!")
        Log.i(TAG, "✅ All code in ApplicationLike CAN be hot updated")
    }
    
    /**
     * 对应 Application.onTerminate()
     * 
     * 注意：此方法在真实设备上永远不会被调用，仅用于模拟器测试
     */
    fun onTerminate() {
        Log.i(TAG, "onTerminate called")
        
        // 清理代码
        cleanup()
    }
    
    /**
     * 对应 Application.onLowMemory()
     * 
     * ✅ 这里的代码会被热更新
     * 当设备内存不足时被调用
     */
    fun onLowMemory() {
        Log.i(TAG, "onLowMemory called")
        
        // ✅ 低内存处理逻辑可以被热更新
        clearCache()
    }
    
    /**
     * 对应 Application.onTrimMemory()
     * 
     * ✅ 这里的代码会被热更新
     * 当系统要求释放内存时被调用
     */
    fun onTrimMemory(level: Int) {
        Log.i(TAG, "onTrimMemory called: level=$level")
        
        // ✅ 内存修剪逻辑可以被热更新
        when (level) {
            android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                Log.d(TAG, "UI hidden, release UI resources")
                releaseUIResources()
            }
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                Log.d(TAG, "Device running moderately slow")
            }
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                Log.d(TAG, "Device running very slow")
                clearCache()
            }
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.d(TAG, "Device running critically slow")
                clearCache()
            }
        }
    }
    
    // ============ 私有方法示例 ============
    
    /**
     * ✅ 初始化第三方 SDK
     * 这些代码都会被热更新
     */
    private fun initSDKs() {
        Log.d(TAG, "Initializing SDKs...")
        
        // 示例：初始化网络库
        // OkHttpClient.Builder().build()
        
        // 示例：初始化图片库
        // Glide.with(context)
        
        // 示例：初始化数据库
        // Room.databaseBuilder(context, AppDatabase::class.java, "app_db").build()
    }
    
    /**
     * ✅ 初始化业务管理类
     * 这些代码都会被热更新
     */
    private fun initManagers() {
        Log.d(TAG, "Initializing managers...")
        
        // 示例：初始化用户管理
        // UserManager.init(context)
        
        // 示例：初始化网络管理
        // NetworkManager.init(context)
        
        // 示例：初始化配置管理
        // ConfigManager.init(context)
    }
    
    /**
     * ✅ 清理缓存
     * 这个方法会被热更新
     */
    private fun clearCache() {
        Log.d(TAG, "Clearing cache...")
        // 实现缓存清理逻辑
    }
    
    /**
     * ✅ 释放 UI 资源
     * 这个方法会被热更新
     */
    private fun releaseUIResources() {
        Log.d(TAG, "Releasing UI resources...")
        // 实现 UI 资源释放逻辑
    }
    
    /**
     * ✅ 清理资源
     * 这个方法会被热更新
     */
    private fun cleanup() {
        Log.d(TAG, "Cleaning up...")
        // 实现清理逻辑
    }
}

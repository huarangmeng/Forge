package com.hrm.forge.demo.manual

import android.content.Context
import android.util.Log

/**
 * ApplicationLike for Manual Integration
 * 
 * ✅ 这个类的所有代码都会被热更新
 * 
 * 与自动集成的 ApplicationLike 相同，代码生效区间完全一致
 */
class ManualApplicationLike(private val context: Context) {
    
    companion object {
        private const val TAG = "ManualApplicationLike"
    }
    
    /**
     * ✅ 这里的代码会被热更新
     */
    fun attachBaseContext(base: Context) {
        Log.i(TAG, "✅ attachBaseContext called (CAN be hot updated)")
        
        // 早期初始化代码
        Log.i(TAG, "Manual integration - early initialization")
    }
    
    /**
     * ✅ 这里的代码会被热更新
     */
    fun onCreate() {
        Log.i(TAG, "✅ onCreate called (CAN be hot updated)")
        
        // 业务初始化代码
        initBusiness()
        
        Log.i(TAG, "✅ Manual demo app initialized successfully!")
        Log.i(TAG, "✅ All code in ApplicationLike CAN be hot updated")
    }
    
    /**
     * ✅ 这里的代码会被热更新
     */
    fun onTerminate() {
        Log.i(TAG, "onTerminate called")
        
        // 清理代码
    }
    
    /**
     * ✅ 这里的代码会被热更新
     */
    fun onLowMemory() {
        Log.i(TAG, "onLowMemory called")
        
        // 低内存处理
    }
    
    /**
     * ✅ 这里的代码会被热更新
     */
    fun onTrimMemory(level: Int) {
        Log.i(TAG, "onTrimMemory called: level=$level")
        
        // 内存修剪处理
    }
    
    /**
     * ✅ 私有方法也会被热更新
     */
    private fun initBusiness() {
        Log.d(TAG, "Initializing business logic...")
        
        // 业务逻辑初始化
        // UserManager.init(context)
        // NetworkManager.init(context)
    }
}

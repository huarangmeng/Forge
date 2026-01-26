package com.hrm.forge.loader.instrumentation

import android.app.Activity
import android.os.Bundle
import com.hrm.forge.logger.Logger

/**
 * 占坑 Activity
 * 
 * 用于启动未在 AndroidManifest 中注册的 Activity
 * 实际的 Activity 类会在 InstrumentationProxy 中动态替换
 */
class StubActivity : Activity() {
    
    private val TAG = "StubActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 这个方法通常不会被调用
        // 因为在 InstrumentationProxy.newActivity() 中已经替换成真实的 Activity
        Logger.w(TAG, "StubActivity.onCreate() should not be called!")
        Logger.w(TAG, "This means the activity replacement failed.")
        
        // 如果真的执行到这里，说明出问题了
        finish()
    }
}

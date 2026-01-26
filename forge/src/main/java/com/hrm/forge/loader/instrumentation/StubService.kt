package com.hrm.forge.loader.instrumentation

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * 占坑 Service
 * 
 * 这是一个空的 Service，用于在 AndroidManifest.xml 中注册
 * 运行时会被替换为真实的 Service
 * 
 * 注意：
 * 1. 必须在主 APK 的 AndroidManifest.xml 中注册
 * 2. 此类不能被混淆
 */
class StubService : Service() {
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onCreate() {
        super.onCreate()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
    }
}

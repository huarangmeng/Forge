package com.hrm.forge.upgrade

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

/**
 * 可绑定的测试 Service
 * 
 * 这个 Service 不会在主 APK 的 AndroidManifest 中注册
 * 用于测试热更新框架的 Service bindService 功能
 * 
 * 测试方法：
 * 1. 构建并部署 upgrade-test APK 到热更新目录
 * 2. 在代码中调用 bindService() 绑定此 Service
 * 3. 检查日志，验证 Service 是否正常绑定和交互
 */
class TestBindService : Service() {
    
    companion object {
        private const val TAG = "TestBindService"
    }
    
    /**
     * Binder 实现
     */
    inner class LocalBinder : Binder() {
        fun getService(): TestBindService = this@TestBindService
    }
    
    private val binder = LocalBinder()
    
    /**
     * 绑定计数器
     */
    private var bindCount = 0
    
    /**
     * 消息计数器
     */
    private var messageCount = 0
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🎉 TestBindService onCreate() - Service 创建成功！")
        Log.i(TAG, "这是来自热更新 APK 的 BindService，未在主 APK AndroidManifest 中注册")
    }
    
    override fun onBind(intent: Intent?): IBinder {
        bindCount++
        Log.i(TAG, "🔗 TestBindService onBind() - 第 $bindCount 次绑定")
        
        // 获取 Intent 中的参数
        val clientName = intent?.getStringExtra("client_name")
        if (clientName != null) {
            Log.i(TAG, "客户端: $clientName")
        }
        
        return binder
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "🔓 TestBindService onUnbind()")
        return true // 允许 onRebind
    }
    
    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        bindCount++
        Log.i(TAG, "🔗 TestBindService onRebind() - 第 $bindCount 次绑定")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "💀 TestBindService onDestroy() - Service 销毁")
        Log.i(TAG, "统计: 共绑定 $bindCount 次，处理 $messageCount 条消息")
    }
    
    /**
     * 供客户端调用的方法
     */
    fun sendMessage(message: String): String {
        messageCount++
        Log.i(TAG, "📨 收到消息 #$messageCount: $message")
        return "✅ 消息已处理: $message"
    }
    
    /**
     * 获取 Service 状态
     */
    fun getStatus(): String {
        return "TestBindService 运行中 - 绑定次数: $bindCount, 消息数: $messageCount"
    }
}

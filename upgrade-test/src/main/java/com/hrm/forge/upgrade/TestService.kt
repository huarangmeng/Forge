package com.hrm.forge.upgrade

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * 测试 Service
 * 
 * 这个 Service 不会在主 APK 的 AndroidManifest 中注册
 * 用于测试热更新框架的 Service Hook 功能
 * 
 * 测试方法：
 * 1. 构建并部署 upgrade-test APK 到热更新目录
 * 2. 使用 HotUpdateTester.testStartService() 启动此 Service
 * 3. 检查日志，验证 Service 是否正常启动和运行
 */
class TestService : Service() {
    
    companion object {
        private const val TAG = "TestService"
    }
    
    /**
     * 启动计数器
     */
    private var startCount = 0
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🎉 TestService onCreate() - Service 创建成功！")
        Log.i(TAG, "这是来自热更新 APK 的 Service，未在主 APK AndroidManifest 中注册")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startCount++
        Log.i(TAG, "▶️ TestService onStartCommand() - 第 $startCount 次启动")
        
        // 获取 Intent 中的参数
        val message = intent?.getStringExtra("test_message")
        if (message != null) {
            Log.i(TAG, "收到消息: $message")
        }
        
        // 模拟一些工作
        Thread {
            try {
                Log.i(TAG, "⏳ TestService 开始执行任务...")
                Thread.sleep(2000)
                Log.i(TAG, "✅ TestService 任务执行完成")
            } catch (e: InterruptedException) {
                Log.e(TAG, "任务被中断", e)
            }
        }.start()
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        Log.i(TAG, "🔗 TestService onBind()")
        // 这个测试 Service 不支持绑定
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "💀 TestService onDestroy() - Service 销毁，共启动了 $startCount 次")
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "⚠️ TestService onLowMemory() - 系统内存不足")
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.w(TAG, "⚠️ TestService onTrimMemory() - level: $level")
    }
}

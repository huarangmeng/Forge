package com.hrm.forge.upgrade

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 动态注册测试 BroadcastReceiver
 * 
 * 这个 Receiver 不会在任何 AndroidManifest 中注册
 * 仅用于测试动态注册功能（通过 context.registerReceiver() 注册）
 * 
 * 测试方法：
 * 1. 构建并部署 包含 upgrade-test module 得 APK 到热更新目录
 * 2. 使用 HotUpdateTester.testRegisterReceiver() 动态注册此 Receiver
 * 3. 使用 HotUpdateTester.testSendImplicitBroadcast() 发送隐式广播
 * 4. 检查日志，验证 Receiver 是否正常接收和处理广播
 * 5. 使用 HotUpdateTester.testUnregisterReceiver() 取消注册
 * 
 * 工作原理：
 * - 通过反射加载热更新 APK 中的 Receiver 类
 * - 使用 context.registerReceiver() 动态注册
 * - DexLoader 已将热更新 DEX 合并到主 ClassLoader，因此可以直接加载
 * - 动态注册的 Receiver 与普通 Receiver 无任何区别
 * 
 * 与 TestReceiver 的区别：
 * - TestReceiver: 静态注册，用于显式广播测试
 * - DynamicTestReceiver: 动态注册，用于隐式广播测试
 */
class DynamicTestReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "DynamicTestReceiver"
        
        /**
         * 接收计数器（静态变量，跨实例保持）
         */
        private var receiveCount = 0
        
        /**
         * 注册时间戳
         */
        private var registerTime = 0L
    }
    
    /**
     * 此方法在动态注册时被调用（通过反射）
     * 用于记录注册时间
     */
    init {
        if (registerTime == 0L) {
            registerTime = System.currentTimeMillis()
            Log.i(TAG, "🎯 DynamicTestReceiver 实例创建")
        }
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        receiveCount++
        
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val runningTime = if (registerTime > 0) {
            (System.currentTimeMillis() - registerTime) / 1000.0
        } else {
            0.0
        }
        
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "📡 DynamicTestReceiver onReceive() - 第 $receiveCount 次接收")
        Log.i(TAG, "⏰ 时间: $timestamp")
        Log.i(TAG, "⏱️ 运行时长: ${String.format("%.1f", runningTime)}s")
        Log.i(TAG, "这是来自热更新 APK 的动态注册 BroadcastReceiver")
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        if (context == null || intent == null) {
            Log.e(TAG, "❌ Context 或 Intent 为 null")
            return
        }
        
        // 输出广播信息
        Log.i(TAG, "📨 广播详情:")
        Log.i(TAG, "  • Action: ${intent.action}")
        Log.i(TAG, "  • Package: ${context.packageName}")
        Log.i(TAG, "  • Component: ${intent.component?.className ?: "null (隐式广播)"}")
        
        // 输出 Intent 中的额外数据
        val extras = intent.extras
        if (extras != null && !extras.isEmpty) {
            Log.i(TAG, "  • Extras:")
            for (key in extras.keySet()) {
                val value = extras.get(key)
                Log.i(TAG, "    - $key: $value")
            }
        } else {
            Log.i(TAG, "  • Extras: (空)")
        }
        
        // 检查是否是有序广播
        if (isOrderedBroadcast) {
            Log.i(TAG, "📋 有序广播:")
            Log.i(TAG, "  • 当前结果码: $resultCode")
            Log.i(TAG, "  • 当前结果数据: $resultData")
            
            // 修改结果（演示有序广播的结果传递）
            setResultCode(200)
            setResultData("DynamicTestReceiver processed")
            
            Log.i(TAG, "  • 已修改结果码: 200")
            Log.i(TAG, "  • 已修改结果数据: DynamicTestReceiver processed")
        } else {
            Log.i(TAG, "📢 普通广播（非有序广播）")
        }
        
        // 模拟一些处理逻辑
        try {
            Log.i(TAG, "⏳ 处理广播中...")
            
            // 获取测试数据
            val testData = intent.getStringExtra("test_data")
            val timestamp_extra = intent.getLongExtra("timestamp", 0)
            
            if (testData != null) {
                Log.i(TAG, "✅ 收到测试数据: $testData")
            }
            
            if (timestamp_extra != 0L) {
                val delay = System.currentTimeMillis() - timestamp_extra
                Log.i(TAG, "⏱️ 广播延迟: ${delay}ms")
            }
            
            // 演示：动态注册可以访问热更新 APK 中的其他类
            try {
                val guideClass = Class.forName("com.hrm.forge.upgrade.ServiceTestGuide")
                Log.i(TAG, "✅ 成功访问热更新 APK 中的其他类: ${guideClass.simpleName}")
            } catch (e: ClassNotFoundException) {
                Log.w(TAG, "⚠️ 未找到 ServiceTestGuide 类（可能不在同一 APK）")
            }
            
            Log.i(TAG, "✅ 广播处理完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理广播时出错", e)
        }
        
        Log.i(TAG, "💡 提示: 使用 HotUpdateTester.testUnregisterReceiver() 可以取消注册")
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "")
    }
    
    /**
     * 清理方法（可选）
     * 在取消注册时可能被调用
     */
    fun cleanup() {
        Log.i(TAG, "🧹 DynamicTestReceiver cleanup()")
        Log.i(TAG, "  • 总共接收了 $receiveCount 次广播")
        Log.i(TAG, "  • 运行时长: ${(System.currentTimeMillis() - registerTime) / 1000.0}s")
    }
}

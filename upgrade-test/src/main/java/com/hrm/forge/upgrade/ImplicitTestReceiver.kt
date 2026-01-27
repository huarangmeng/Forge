package com.hrm.forge.upgrade

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 隐式广播测试 Receiver
 *
 * 用于验证热更新框架能否：
 * 1. 解析热更新 APK 的 IntentFilter 配置
 * 2. 拦截隐式广播并手动分发到热更新 Receiver
 * 3. 正确处理优先级和重复分发
 *
 * **注册方式：** 在 AndroidManifest.xml 中静态注册（仅用于测试）
 *
 * **测试目的：**
 * - 验证 AMSHook 能否拦截隐式广播
 * - 验证 ComponentManager 能否正确解析 IntentFilter
 * - 验证 Receiver 实例缓存机制
 *
 * **重要说明：**
 * 这个 Receiver 演示了"伪静态注册"功能，即：
 * - 在热更新 APK 的 Manifest 中声明 Receiver 和 IntentFilter
 * - Forge 框架自动解析并拦截匹配的隐式广播
 * - 手动创建 Receiver 实例并调用 onReceive()
 *
 * **限制：**
 * - 应用必须正在运行（进程存活）
 * - Android 8.0+ 的系统隐式广播限制仍然生效
 * - 无法在应用未运行时接收广播（与真正的静态注册不同）
 */
class ImplicitTestReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ImplicitTestReceiver"

        // 接收次数计数器
        private var receiveCount = 0

        // 首次接收时间
        private var firstReceiveTime: Long = 0
    }

    init {
        // 实例创建时打印日志
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        Log.i(TAG, "🎯 ImplicitTestReceiver 实例创建 - $timestamp")
        Log.i(TAG, "📍 实例 hashCode: ${hashCode()}")
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.w(TAG, "⚠️ context or intent is null")
            return
        }

        // 增加接收计数
        receiveCount++

        // 记录首次接收时间
        if (firstReceiveTime == 0L) {
            firstReceiveTime = System.currentTimeMillis()
        }

        // 计算运行时长
        val uptime = (System.currentTimeMillis() - firstReceiveTime) / 1000.0

        // 格式化时间
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())

        // 提取广播信息
        val action = intent.action ?: "No Action"
        val testData = intent.getStringExtra("test_data") ?: "No Data"
        val extraTimestamp = intent.getLongExtra("timestamp", 0L)

        // 打印详细日志
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "📡 ImplicitTestReceiver onReceive() - 第 $receiveCount 次接收")
        Log.i(TAG, "⏰ 时间: $timestamp")
        Log.i(TAG, "⏱️ 运行时长: ${String.format("%.1f", uptime)}s")
        Log.i(TAG, "📦 实例 hashCode: ${hashCode()}")
        Log.i(TAG, "")
        Log.i(TAG, "📋 广播信息:")
        Log.i(TAG, "  ├─ Action: $action")
        Log.i(TAG, "  ├─ 测试数据: $testData")
        Log.i(TAG, "  ├─ 发送时间戳: $extraTimestamp")
        Log.i(TAG, "  └─ Package: ${context.packageName}")
        Log.i(TAG, "")
        Log.i(TAG, "✅ 广播处理完成")
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
}

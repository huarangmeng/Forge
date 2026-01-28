package com.hrm.forge.demo

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.widget.Toast
import java.util.logging.Logger

/**
 * 热更新测试工具类
 *
 * 用于验证热更新后的功能，包括：
 * - 新增 Activity 能否正常启动
 * - 新增 Service 能否正常启动
 * - 新增 BroadcastReceiver 能否正常接收广播
 * - 新增类能否正常加载
 * - 资源文件能否正常访问
 */
object HotUpdateTester {
    private const val TAG = "HotUpdateTester"
    
    // 保存动态注册的 Receiver（用于测试和管理）
    private val registeredReceivers = mutableMapOf<String, BroadcastReceiver>()

    /**
     * 测试启动指定的 Activity
     *
     * @param context 上下文
     * @param activityClassName Activity 完整类名（如 "com.hrm.forge.demo.NewActivity"）
     * @return 是否成功启动
     */
    fun testLaunchActivity(context: Context, activityClassName: String): Boolean {
        return try {
            Log.i(TAG, "Testing launch activity: $activityClassName")

            // 直接创建 Intent 并启动，不做类检查
            // 如果 Activity 不存在或启动失败，系统会抛出异常
            val intent = Intent()
            intent.component = ComponentName(context.packageName, activityClassName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            Log.i(TAG, "Activity launch request sent successfully")
            showToast(context, "✅ 成功启动 Activity")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch activity: $activityClassName", e)
            showToast(context, "❌ 启动失败：${e.javaClass.simpleName}")
            false
        }
    }

    /**
     * 测试启动指定的 Service
     *
     * @param context 上下文
     * @param serviceClassName Service 完整类名（如 "com.hrm.forge.demo.TestService"）
     * @return 是否成功启动
     */
    fun testStartService(context: Context, serviceClassName: String): Boolean {
        return try {
            Log.i(TAG, "Testing start service: $serviceClassName")

            // 直接创建 Intent 并启动 Service
            val intent = Intent()
            intent.component = ComponentName(context.packageName, serviceClassName)
            context.startService(intent)

            Log.i(TAG, "Service start request sent successfully")
            showToast(context, "✅ 成功启动 Service")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service: $serviceClassName", e)
            showToast(context, "❌ 启动失败：${e.javaClass.simpleName}")
            false
        }
    }

    /**
     * 测试停止指定的 Service
     *
     * @param context 上下文
     * @param serviceClassName Service 完整类名
     * @return 是否成功停止
     */
    fun testStopService(context: Context, serviceClassName: String): Boolean {
        return try {
            Log.i(TAG, "Testing stop service: $serviceClassName")

            val intent = Intent()
            intent.component = ComponentName(context.packageName, serviceClassName)
            val result = context.stopService(intent)

            Log.i(TAG, "Service stop request sent, result: $result")
            showToast(context, "✅ Service 已停止")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop service: $serviceClassName", e)
            showToast(context, "❌ 停止失败：${e.javaClass.simpleName}")
            false
        }
    }

    /**
     * 测试动态注册 BroadcastReceiver
     *
     * @param context 上下文
     * @param receiverClassName BroadcastReceiver 完整类名
     * @param action 要监听的广播 Action
     * @return 是否成功注册
     */
    fun testRegisterReceiver(
        context: Context,
        receiverClassName: String,
        action: String = "com.hrm.forge.DYNAMIC_ACTION"
    ): Boolean {
        return try {
            Log.i(TAG, "Testing register receiver: $receiverClassName, action: $action")

            // 检查是否已注册
            if (registeredReceivers.containsKey(receiverClassName)) {
                showToast(context, "⚠️ Receiver 已注册，请先取消注册")
                return false
            }

            // 使用反射加载并实例化 Receiver（验证热更新的类能被加载）
            val receiverClass = Class.forName(receiverClassName)
            val receiver = receiverClass.newInstance() as BroadcastReceiver

            // 创建 IntentFilter
            val filter = IntentFilter(action)

            // 动态注册
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            }

            // 保存引用
            registeredReceivers[receiverClassName] = receiver

            Log.i(TAG, "Receiver registered successfully")
            showToast(context, "✅ 已动态注册 Receiver")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register receiver: $receiverClassName", e)
            showToast(context, "❌ 注册失败：${e.message}")
            false
        }
    }

    /**
     * 测试取消注册 BroadcastReceiver
     *
     * @param context 上下文
     * @param receiverClassName BroadcastReceiver 完整类名
     * @return 是否成功取消注册
     */
    fun testUnregisterReceiver(context: Context, receiverClassName: String): Boolean {
        return try {
            Log.i(TAG, "Testing unregister receiver: $receiverClassName")

            val receiver = registeredReceivers[receiverClassName]
            if (receiver == null) {
                showToast(context, "⚠️ Receiver 未注册")
                return false
            }

            // 取消注册
            context.unregisterReceiver(receiver)

            // 移除引用
            registeredReceivers.remove(receiverClassName)

            Log.i(TAG, "Receiver unregistered successfully")
            showToast(context, "✅ 已取消注册 Receiver")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver: $receiverClassName", e)
            showToast(context, "❌ 取消失败：${e.javaClass.simpleName}")
            false
        }
    }

    /**
     * 测试发送隐式广播（用于测试动态注册的 Receiver）
     *
     * @param context 上下文
     * @param action 广播 Action
     * @return 是否成功发送
     */
    fun testSendImplicitBroadcast(context: Context, action: String): Boolean {
        return try {
            Log.i(TAG, "Testing send implicit broadcast, action: $action")

            val intent = Intent(action)
            intent.putExtra("test_data", "Hello from implicit broadcast")
            intent.putExtra("timestamp", System.currentTimeMillis())

            context.sendBroadcast(intent)

            Log.i(TAG, "Implicit broadcast sent successfully")
            showToast(context, "✅ 已发送隐式广播")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send implicit broadcast", e)
            showToast(context, "❌ 发送失败：${e.javaClass.simpleName}")
            false
        }
    }
    
    /**
     * 测试发送自定义隐式广播（用于测试"伪静态注册"）
     *
     * @param context 上下文
     * @param action 广播 Action
     * @return 是否成功发送
     */
    fun testSendCustomImplicitBroadcast(
        context: Context,
        action: String = "com.hrm.forge.IMPLICIT_TEST_ACTION"
    ): Boolean {
        return try {
            Log.i(TAG, "Testing send custom implicit broadcast, action: $action")

            val intent = Intent(action)
            intent.putExtra("test_data", "Testing implicit broadcast from HotUpdateTester")
            intent.putExtra("timestamp", System.currentTimeMillis())

            context.sendBroadcast(intent)

            Log.i(TAG, "Custom implicit broadcast sent successfully")
            showToast(context, "✅ 已发送自定义隐式广播")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send custom implicit broadcast", e)
            showToast(context, "❌ 发送失败：${e.javaClass.simpleName}")
            false
        }
    }
    
    /**
     * 测试 ContentProvider 查询操作
     * 
     * @param context 上下文
     * @param authority ContentProvider 的 Authority
     * @param path 查询路径（可选，如 "users"）
     * @return 是否成功
     */
    fun testQueryProvider(context: Context, authority: String, path: String? = null): Boolean {
        return try {
            Log.i(TAG, "Testing query provider: $authority${path?.let { "/$it" } ?: ""}")
            
            val uri = if (path != null) {
                android.net.Uri.parse("content://$authority/$path")
            } else {
                android.net.Uri.parse("content://$authority")
            }
            
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            
            if (cursor != null) {
                val count = cursor.count
                val columns = cursor.columnNames.joinToString()
                cursor.close()
                
                Log.i(TAG, "Query successful: $count rows, columns: [$columns]")
                showToast(context, "✅ 查询成功：$count 行数据")
                true
            } else {
                Log.w(TAG, "Query returned null cursor")
                showToast(context, "⚠️ 查询返回空")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query provider: $authority", e)
            showToast(context, "❌ 查询失败：${e.message}")
            false
        }
    }
    
    /**
     * 测试 ContentProvider 插入操作
     * 
     * @param context 上下文
     * @param authority ContentProvider 的 Authority
     * @param path 插入路径（可选，如 "users"）
     * @return 是否成功
     */
    fun testInsertProvider(context: Context, authority: String, path: String? = null): Boolean {
        return try {
            Log.i(TAG, "Testing insert provider: $authority${path?.let { "/$it" } ?: ""}")
            
            val uri = if (path != null) {
                android.net.Uri.parse("content://$authority/$path")
            } else {
                android.net.Uri.parse("content://$authority")
            }
            
            val values = android.content.ContentValues().apply {
                put("name", "Test User ${System.currentTimeMillis()}")
                put("age", 25)
                put("timestamp", System.currentTimeMillis())
            }
            
            val resultUri = context.contentResolver.insert(uri, values)
            
            if (resultUri != null) {
                Log.i(TAG, "Insert successful: $resultUri")
                showToast(context, "✅ 插入成功：$resultUri")
                true
            } else {
                Log.w(TAG, "Insert returned null uri")
                showToast(context, "⚠️ 插入返回空")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert provider: $authority", e)
            showToast(context, "❌ 插入失败：${e.message}")
            false
        }
    }

    private fun showToast(context: Context, message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}

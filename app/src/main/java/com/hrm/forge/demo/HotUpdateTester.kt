package com.hrm.forge.demo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.hrm.forge.logger.Logger

/**
 * 热更新测试工具类
 *
 * 用于验证热更新后的功能，包括：
 * - 新增 Activity 能否正常启动
 * - 新增类能否正常加载
 * - 资源文件能否正常访问
 */
object HotUpdateTester {
    private const val TAG = "HotUpdateTester"

    /**
     * 测试启动指定的 Activity
     *
     * @param context 上下文
     * @param activityClassName Activity 完整类名（如 "com.hrm.forge.demo.NewActivity"）
     * @return 是否成功启动
     */
    fun testLaunchActivity(context: Context, activityClassName: String): Boolean {
        return try {
            Logger.i(TAG, "Testing launch activity: $activityClassName")

            // 直接创建 Intent 并启动，不做类检查
            // 如果 Activity 不存在或启动失败，系统会抛出异常
            val intent = Intent()
            intent.component = android.content.ComponentName(context.packageName, activityClassName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            Logger.i(TAG, "Activity launch request sent successfully")
            showToast(context, "✅ 成功启动 Activity")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to launch activity: $activityClassName", e)
            showToast(context, "❌ 启动失败：${e.javaClass.simpleName}")
            false
        }
    }

    private fun showToast(context: Context, message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}

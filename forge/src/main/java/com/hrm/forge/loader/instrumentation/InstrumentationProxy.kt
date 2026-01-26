package com.hrm.forge.loader.instrumentation

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import com.hrm.forge.logger.Logger

/**
 * Instrumentation 代理
 *
 * 用于支持动态加载 Activity，包括：
 * 1. 监控 Activity 启动流程
 * 2. 支持启动未在 AndroidManifest 中注册的 Activity（占坑模式）
 *
 * 工作原理：
 * - execStartActivity: Hook Activity 启动，未注册的 Activity 替换为占坑 Activity
 * - newActivity: Hook Activity 创建，将占坑 Activity 替换回真实 Activity
 */
class InstrumentationProxy(private val base: Instrumentation) : Instrumentation() {

    private val TAG = "InstrumentationProxy"

    companion object {
        // 用于在 Intent 中保存真实 Activity 信息的 key
        private const val KEY_REAL_ACTIVITY = "intent_real_class_name"
    }

    /**
     * Hook Activity 启动
     * 注意：此方法不能被混淆
     *
     * 占坑模式核心逻辑：
     * 1. 检查目标 Activity 是否已在 AndroidManifest 中注册
     * 2. 如果未注册，检查是否在热更新 APK 中存在
     * 3. 如果存在，替换为占坑 Activity
     * 4. 如果不存在，抛出异常阻止启动
     * 5. 保存真实 Activity 信息，在 newActivity 时恢复
     */
    @Throws(Exception::class)
    fun execStartActivity(
        who: Context?,
        contextThread: IBinder?,
        token: IBinder?,
        target: Activity?,
        intent: Intent?,
        requestCode: Int,
        options: Bundle?
    ): ActivityResult? {  // 返回值可能为 null

        Logger.d(TAG, "execStartActivity called")

        // 处理 Intent
        if (intent != null && intent.component != null) {
            val targetClass = intent.component?.className
            Logger.d(TAG, "Target activity: $targetClass")

            // 检查目标 Activity 是否已注册
            if (targetClass != null && who != null) {
                val isRegisteredInMain = ActivityInfoManager.isActivityRegisteredInMain(targetClass)

                if (!isRegisteredInMain) {
                    Logger.i(TAG, "⚠️ Activity not registered in main APK: $targetClass")

                    // 检查是否在热更新 APK 中存在
                    val isInHotUpdate = ActivityInfoManager.isActivityInHotUpdate(targetClass)

                    if (isInHotUpdate) {
                        Logger.i(TAG, "✅ Activity found in hot update APK")
                        Logger.i(TAG, "🔄 Using stub activity for replacement")

                        // 保存真实 Activity 信息到 Intent
                        intent.putExtra(KEY_REAL_ACTIVITY, targetClass)

                        // 根据启动模式选择对应的占坑 Activity
                        val stubActivity =
                            ActivityInfoManager.getStubActivityForRealActivity(targetClass)
                        val stubComponent = ComponentName(who.packageName, stubActivity)
                        intent.component = stubComponent

                        Logger.i(TAG, "✅ Replaced with stub activity: $stubActivity")
                    } else {
                        // Activity 既不在主 APK 中，也不在热更新 APK 中
                        Logger.e(TAG, "❌ Activity not found: $targetClass")
                        Logger.e(TAG, "   - Not in main APK")
                        Logger.e(TAG, "   - Not in hot update APK")
                        throw ClassNotFoundException("Activity not found in main APK or hot update APK: $targetClass")
                    }
                } else {
                    Logger.d(TAG, "✅ Activity registered in main APK")
                }
            }
        }

        // 使用反射调用原始的 execStartActivity 方法
        val result =
            normalStartActivity(who, contextThread, token, target, intent, requestCode, options)
        Logger.d(TAG, "execStartActivity completed successfully")
        return result
    }

    /**
     * 使用反射调用原始 Instrumentation 的 execStartActivity 方法
     */
    @SuppressLint("PrivateApi")
    @Throws(Exception::class)
    private fun normalStartActivity(
        who: Context?,
        contextThread: IBinder?,
        token: IBinder?,
        target: Activity?,
        intent: Intent?,
        requestCode: Int,
        options: Bundle?
    ): ActivityResult? {  // 返回值可能为 null

        val execMethod = Instrumentation::class.java.getDeclaredMethod(
            "execStartActivity",
            Context::class.java,
            IBinder::class.java,
            IBinder::class.java,
            Activity::class.java,
            Intent::class.java,
            Int::class.javaPrimitiveType,
            Bundle::class.java
        )

        return try {
            execMethod.invoke(
                base,
                who,
                contextThread,
                token,
                target,
                intent,
                requestCode,
                options
            ) as? ActivityResult  // 使用安全转换，允许 null
        } catch (e: java.lang.reflect.InvocationTargetException) {
            // 反射调用的异常会被包装成 InvocationTargetException
            // 取出真正的异常并抛出
            throw e.targetException ?: e
        }
    }

    /**
     * Hook Activity 创建
     * 注意：此方法不能被混淆
     *
     * 占坑模式核心逻辑：
     * 1. 检查 Intent 中是否包含真实 Activity 信息
     * 2. 如果是占坑 Activity，替换为真实 Activity
     * 3. 使用热更新的 ClassLoader 加载真实 Activity
     */
    @Throws(
        InstantiationException::class,
        IllegalAccessException::class,
        ClassNotFoundException::class
    )
    override fun newActivity(cl: ClassLoader, className: String, intent: Intent): Activity {
        Logger.d(TAG, "newActivity: className=$className")

        // 设置 Intent 的 ClassLoader（关键！）
        intent.setExtrasClassLoader(cl)

        // 检查是否是占坑 Activity
        val realActivityClass = intent.getStringExtra(KEY_REAL_ACTIVITY)

        val activity: Activity = if (!realActivityClass.isNullOrEmpty()) {
            // 这是一个占坑 Activity，需要替换为真实 Activity
            Logger.i(TAG, "🔄 Stub activity detected")
            Logger.i(TAG, "Stub: $className")
            Logger.i(TAG, "Real: $realActivityClass")

            // 使用原始 Instrumentation 和热更新的 ClassLoader 加载真实 Activity
            base.newActivity(cl, realActivityClass, intent).also {
                Logger.i(TAG, "✅ Real activity created successfully: $realActivityClass")
            }
        } else {
            // 正常 Activity，直接创建
            base.newActivity(cl, className, intent).also {
                Logger.d(TAG, "✅ Activity created successfully: $className")
            }
        }

        return activity
    }

    /**
     * 委托其他方法到 base
     */
    override fun onCreate(arguments: Bundle?) {
        base.onCreate(arguments)
    }

    override fun start() {
        base.start()
    }

    override fun onStart() {
        base.onStart()
    }

    override fun onException(obj: Any?, e: Throwable?): Boolean {
        return base.onException(obj, e)
    }

    override fun onDestroy() {
        base.onDestroy()
    }

    override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
        base.callActivityOnCreate(activity, icicle)
    }

    override fun callActivityOnDestroy(activity: Activity) {
        base.callActivityOnDestroy(activity)
    }

    override fun callActivityOnResume(activity: Activity) {
        base.callActivityOnResume(activity)
    }

    override fun callActivityOnPause(activity: Activity) {
        base.callActivityOnPause(activity)
    }

    override fun callActivityOnStop(activity: Activity) {
        base.callActivityOnStop(activity)
    }

    override fun callActivityOnRestart(activity: Activity) {
        base.callActivityOnRestart(activity)
    }
}

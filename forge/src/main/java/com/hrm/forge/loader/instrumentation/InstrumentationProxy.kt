package com.hrm.forge.loader.instrumentation

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import com.hrm.forge.logger.Logger
import java.lang.reflect.Method

/**
 * Instrumentation 代理
 * 用于支持动态加载未注册的 Activity
 */
class InstrumentationProxy(private val base: Instrumentation) : Instrumentation() {
    
    private val TAG = "InstrumentationProxy"
    
    /**
     * Hook Activity 启动
     * 注意：此方法不能被混淆
     */
    fun execStartActivity(
        who: Context?,
        contextThread: IBinder?,
        token: IBinder?,
        target: Activity?,
        intent: Intent,
        requestCode: Int,
        options: Bundle?
    ): Any? {
        Logger.d(TAG, "execStartActivity: ${intent.component}")
        
        try {
            // 调用原始方法
            val execStartActivityMethod = findExecStartActivityMethod()
            return execStartActivityMethod.invoke(
                base,
                who,
                contextThread,
                token,
                target,
                intent,
                requestCode,
                options
            )
        } catch (e: Exception) {
            Logger.e(TAG, "execStartActivity failed", e)
            throw e
        }
    }
    
    /**
     * Hook Activity 创建
     * 注意：此方法不能被混淆
     */
    override fun newActivity(cl: ClassLoader, className: String, intent: Intent): Activity {
        Logger.d(TAG, "newActivity: $className")
        
        return try {
            base.newActivity(cl, className, intent)
        } catch (e: Exception) {
            Logger.e(TAG, "newActivity failed: $className", e)
            throw e
        }
    }
    
    /**
     * 查找 execStartActivity 方法
     */
    private fun findExecStartActivityMethod(): Method {
        val methods = Instrumentation::class.java.getDeclaredMethods()
        
        for (method in methods) {
            if (method.name == "execStartActivity") {
                val paramTypes = method.parameterTypes
                if (paramTypes.size >= 6) {
                    method.isAccessible = true
                    return method
                }
            }
        }
        
        throw NoSuchMethodException("execStartActivity not found")
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

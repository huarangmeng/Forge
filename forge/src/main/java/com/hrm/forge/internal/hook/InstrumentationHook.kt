package com.hrm.forge.internal.hook

import android.annotation.SuppressLint
import android.app.Application
import android.app.Instrumentation
import com.hrm.forge.internal.hook.base.BaseHook
import com.hrm.forge.internal.util.ReflectUtil
import com.hrm.forge.internal.log.Logger

/**
 * Instrumentation Hook（重构版本）
 * 
 * 使用 BaseHook 统一生命周期管理
 * 使用 ReflectUtil 简化反射代码
 */
internal object InstrumentationHook : BaseHook() {
    
    override val tag = "InstrumentationHook"
    
    private const val ACTIVITY_THREAD_CLASS = "android.app.ActivityThread"
    
    /**
     * 原始的 Instrumentation（用于 unhook）
     */
    private var originalInstrumentation: Instrumentation? = null
    
    /**
     * Hook Instrumentation（公开方法，保持兼容性）
     */
    fun hookInstrumentation() {
        hook()
    }
    
    @SuppressLint("PrivateApi")
    override fun doHook() {
        // 1. 获取 ActivityThread
        val activityThreadClass = ReflectUtil.getClass(ACTIVITY_THREAD_CLASS)
            ?: throw IllegalStateException("Failed to get ActivityThread class")
        
        val activityThread = ReflectUtil.invokeStaticMethod<Any>(
            activityThreadClass,
            "currentActivityThread"
        ) ?: throw IllegalStateException("Failed to get ActivityThread instance")
        
        Logger.d(tag, "Got ActivityThread instance")
        
        // 2. 获取原始 Instrumentation
        originalInstrumentation = ReflectUtil.getFieldValue<Instrumentation>(
            activityThread,
            activityThreadClass,
            "mInstrumentation"
        ) ?: throw IllegalStateException("Failed to get mInstrumentation")
        
        Logger.d(tag, "Got original Instrumentation: ${originalInstrumentation!!.javaClass.name}")
        
        // 3. 创建代理 Instrumentation
        val proxyInstrumentation = InstrumentationProxy(originalInstrumentation!!)
        
        // 4. 替换 Instrumentation
        val replaced = ReflectUtil.setFieldValue(
            activityThread,
            activityThreadClass,
            "mInstrumentation",
            proxyInstrumentation
        )
        
        if (!replaced) {
            throw IllegalStateException("Failed to replace Instrumentation")
        }
        
        Logger.d(tag, "Replaced Instrumentation with proxy")
    }
    
    @SuppressLint("PrivateApi")
    override fun doUnhook() {
        if (originalInstrumentation == null) {
            Logger.w(tag, "Original Instrumentation is null, cannot unhook")
            return
        }
        
        // 获取 ActivityThread
        val activityThreadClass = ReflectUtil.getClass(ACTIVITY_THREAD_CLASS)
            ?: throw IllegalStateException("Failed to get ActivityThread class")
        
        val activityThread = ReflectUtil.invokeStaticMethod<Any>(
            activityThreadClass,
            "currentActivityThread"
        ) ?: throw IllegalStateException("Failed to get ActivityThread instance")
        
        // 恢复原始 Instrumentation
        val restored = ReflectUtil.setFieldValue(
            activityThread,
            activityThreadClass,
            "mInstrumentation",
            originalInstrumentation
        )
        
        if (restored) {
            Logger.d(tag, "Restored original Instrumentation")
            originalInstrumentation = null
        } else {
            throw IllegalStateException("Failed to restore Instrumentation")
        }
    }
    
    /**
     * 恢复原始 Instrumentation（公开方法，保持兼容性）
     */
    @SuppressLint("PrivateApi")
    fun unhookInstrumentation(application: Application) {
        unhook()
    }
}

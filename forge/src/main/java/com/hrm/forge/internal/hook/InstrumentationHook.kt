package com.hrm.forge.internal.hook

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import com.hrm.forge.internal.log.Logger

/**
 * Hook 辅助类
 * 用于替换系统的 Instrumentation
 */
internal object InstrumentationHook {
    private const val TAG = "HookHelper"
    
    /**
     * Hook ActivityThread 的 Instrumentation
     */
    @SuppressLint("PrivateApi")
    fun hookInstrumentation() {
        Logger.i(TAG, "Start hook instrumentation")
        
        try {
            // 获取 ActivityThread
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread")
            currentActivityThreadMethod.isAccessible = true
            val activityThread = currentActivityThreadMethod.invoke(null)
            
            // 获取原始 Instrumentation
            val mInstrumentationField = activityThreadClass.getDeclaredField("mInstrumentation")
            mInstrumentationField.isAccessible = true
            val instrumentation = mInstrumentationField.get(activityThread) as Instrumentation
            
            // 创建代理 Instrumentation
            val proxyInstrumentation = InstrumentationProxy(instrumentation)
            
            // 替换 Instrumentation
            mInstrumentationField.set(activityThread, proxyInstrumentation)
            
            Logger.i(TAG, "Hook instrumentation success")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Hook instrumentation failed", e)
        }
    }
    
    /**
     * 恢复原始 Instrumentation
     */
    @SuppressLint("PrivateApi")
    fun unhookInstrumentation(application: Application) {
        Logger.i(TAG, "Start unhook instrumentation")
        
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread")
            currentActivityThreadMethod.isAccessible = true
            val activityThread = currentActivityThreadMethod.invoke(null)
            
            val mInstrumentationField = activityThreadClass.getDeclaredField("mInstrumentation")
            mInstrumentationField.isAccessible = true
            val instrumentation = mInstrumentationField.get(activityThread)
            
            if (instrumentation is InstrumentationProxy) {
                // 获取原始 Instrumentation
                val baseField = InstrumentationProxy::class.java.getDeclaredField("base")
                baseField.isAccessible = true
                val baseInstrumentation = baseField.get(instrumentation) as Instrumentation
                
                // 恢复原始 Instrumentation
                mInstrumentationField.set(activityThread, baseInstrumentation)
                
                Logger.i(TAG, "Unhook instrumentation success")
            }
            
        } catch (e: Exception) {
            Logger.e(TAG, "Unhook instrumentation failed", e)
        }
    }
}

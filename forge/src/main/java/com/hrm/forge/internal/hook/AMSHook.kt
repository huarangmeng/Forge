package com.hrm.forge.internal.hook

import android.annotation.SuppressLint
import android.content.Context
import com.hrm.forge.internal.hook.base.BaseHook
import com.hrm.forge.internal.hook.base.MethodNameInvocationHandler
import com.hrm.forge.internal.util.ReflectUtil
import com.hrm.forge.internal.log.Logger
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * AMS (ActivityManagerService) Hook 辅助类（重构版本）
 * 
 * 使用策略模式 + 基类重构后的版本
 * 
 * 工作原理：
 * 1. 获取系统的 IActivityManager 实例（AMS 的 Binder 代理）
 * 2. 创建一个动态代理来拦截所有方法调用
 * 3. 在 startService/bindService/broadcastIntent 等方法中处理未注册的组件
 * 4. 其他方法直接转发给原始的 IActivityManager
 * 
 * 优化点：
 * - 继承 BaseHook，统一生命周期管理
 * - 使用 ReflectUtil，消除重复反射代码
 * - 使用 MethodNameInvocationHandler，简化拦截逻辑
 */
internal object AMSHook : BaseHook() {
    
    override val tag = "AMSHook"
    
    private lateinit var context: Context
    
    private const val I_ACTIVITY_MANAGER_CLASS = "android.app.IActivityManager"
    private const val ACTIVITY_MANAGER_CLASS = "android.app.ActivityManager"
    private const val ACTIVITY_MANAGER_NATIVE_CLASS = "android.app.ActivityManagerNative"
    
    /**
     * Hook AMS（公开方法，保持兼容性）
     */
    fun hookAMS(context: Context) {
        this.context = context
        hook()
    }
    
    @SuppressLint("PrivateApi")
    override fun doHook() {
        // 1. 获取 IActivityManager 实例
        val iActivityManager = getIActivityManager()
            ?: throw IllegalStateException("Failed to get IActivityManager")
        
        Logger.d(tag, "Got IActivityManager: ${iActivityManager.javaClass.name}")
        
        // 2. 创建动态代理
        val proxyAMS = Proxy.newProxyInstance(
            context.classLoader,
            arrayOf(Class.forName(I_ACTIVITY_MANAGER_CLASS)),
            AMSInvocationHandler(iActivityManager, context)
        )
        
        Logger.d(tag, "Created AMS proxy: ${proxyAMS.javaClass.name}")
        
        // 3. 替换系统的 IActivityManager
        replaceIActivityManager(proxyAMS)
    }
    
    /**
     * 获取系统的 IActivityManager 实例
     * 
     * 兼容不同 Android 版本：
     * - Android 8.0 (API 26) 之前：ActivityManagerNative.getDefault()
     * - Android 8.0 (API 26) 及之后：ActivityManager.IActivityManagerSingleton
     */
    @SuppressLint("PrivateApi")
    private fun getIActivityManager(): Any? {
        // 先尝试 Android 8.0+ 的方式
        ReflectUtil.getClass(ACTIVITY_MANAGER_CLASS)?.let { activityManagerClass ->
            ReflectUtil.getSingletonInstance<Any>(
                activityManagerClass,
                "IActivityManagerSingleton"
            )?.let { instance ->
                Logger.d(tag, "Got IActivityManager via ActivityManager.IActivityManagerSingleton")
                return instance
            }
        }
        
        // 尝试 Android 8.0 之前的方式
        ReflectUtil.getClass(ACTIVITY_MANAGER_NATIVE_CLASS)?.let { activityManagerNativeClass ->
            ReflectUtil.invokeStaticMethod<Any>(
                activityManagerNativeClass,
                "getDefault"
            )?.let { instance ->
                Logger.d(tag, "Got IActivityManager via ActivityManagerNative.getDefault()")
                return instance
            }
        }
        
        Logger.e(tag, "Failed to get IActivityManager")
        return null
    }
    
    /**
     * 替换系统的 IActivityManager
     */
    @SuppressLint("PrivateApi")
    private fun replaceIActivityManager(proxy: Any) {
        // 先尝试 Android 8.0+ 的方式
        ReflectUtil.getClass(ACTIVITY_MANAGER_CLASS)?.let { activityManagerClass ->
            if (ReflectUtil.replaceSingletonInstance(
                    activityManagerClass,
                    "IActivityManagerSingleton",
                    proxy
                )) {
                Logger.d(tag, "Replaced IActivityManager via ActivityManager.IActivityManagerSingleton")
                return
            }
        }
        
        // 尝试 Android 8.0 之前的方式
        ReflectUtil.getClass(ACTIVITY_MANAGER_NATIVE_CLASS)?.let { activityManagerNativeClass ->
            if (ReflectUtil.replaceSingletonInstance(
                    activityManagerNativeClass,
                    "gDefault",
                    proxy
                )) {
                Logger.d(tag, "Replaced IActivityManager via ActivityManagerNative.gDefault")
                return
            }
        }
        
        throw IllegalStateException("Failed to replace IActivityManager")
    }
    
    /**
     * AMS 方法调用处理器（使用 MethodNameInvocationHandler 简化）
     */
    private class AMSInvocationHandler(
        base: Any,
        private val context: Context
    ) : MethodNameInvocationHandler(
        base,
        "AMSInvocationHandler",
        "startService",
        "bindService",
        "bindIsolatedService",
        "registerReceiver",
        "registerReceiverWithFeature",
        "broadcastIntent",
        "broadcastIntentWithFeature"
    ) {
        
        override fun handleIntercept(method: Method, args: Array<out Any?>?): Any? {
            return when (method.name) {
                "startService" -> handleStartService(args)
                "bindService", "bindIsolatedService" -> handleBindService(args)
                "registerReceiver", "registerReceiverWithFeature" -> handleRegisterReceiver()
                "broadcastIntent", "broadcastIntentWithFeature" -> handleBroadcastIntent(args)
                else -> INVOKE_ORIGINAL
            }
        }
        
        /**
         * 处理 startService 方法
         * 方法签名：ComponentName startService(IApplicationThread caller, Intent service, String resolvedType, ...)
         */
        private fun handleStartService(args: Array<out Any?>?): Any? {
            Logger.d(tag, "Intercepting startService")
            
            // Intent 通常是第二个参数（索引 1）
            getIntent(args, 1)?.let { intent ->
                ComponentManager.processStartServiceIntent(context, intent)
            }
            
            return INVOKE_ORIGINAL
        }
        
        /**
         * 处理 bindService 方法
         * 方法签名：int bindService(IApplicationThread caller, IBinder token, Intent service, String resolvedType, ...)
         */
        private fun handleBindService(args: Array<out Any?>?): Any? {
            Logger.d(tag, "Intercepting bindService")
            
            // Intent 通常是第三个参数（索引 2）
            getIntent(args, 2)?.let { intent ->
                ComponentManager.processBindServiceIntent(context, intent)
            }
            
            return INVOKE_ORIGINAL
        }
        
        /**
         * 处理 registerReceiver 方法（动态注册广播）
         * 注意：动态注册的 Receiver 不需要特殊处理
         */
        private fun handleRegisterReceiver(): Any? {
            Logger.d(tag, "Intercepting registerReceiver")
            return INVOKE_ORIGINAL
        }
        
        /**
         * 处理 broadcastIntent 方法（发送广播）
         * 方法签名：int broadcastIntent(IApplicationThread caller, Intent intent, String resolvedType, ...)
         */
        private fun handleBroadcastIntent(args: Array<out Any?>?): Any? {
            Logger.d(tag, "Intercepting broadcastIntent")
            
            // Intent 通常是第二个参数（索引 1）
            getIntent(args, 1)?.let { intent ->
                ComponentManager.processBroadcastIntent(context, intent)
            }
            
            return INVOKE_ORIGINAL
        }
    }
}

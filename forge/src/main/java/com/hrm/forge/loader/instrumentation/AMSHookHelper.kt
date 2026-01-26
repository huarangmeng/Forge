package com.hrm.forge.loader.instrumentation

import android.annotation.SuppressLint
import android.content.Context
import com.hrm.forge.logger.Logger
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * AMS (ActivityManagerService) Hook 辅助类
 * 
 * 通过 Hook IActivityManager 接口来拦截 Service 相关的系统调用
 * 
 * 工作原理：
 * 1. 获取系统的 IActivityManager 实例（AMS 的 Binder 代理）
 * 2. 创建一个动态代理来拦截所有方法调用
 * 3. 在 startService/bindService 等方法中，将未注册的 Service 替换为 StubService
 * 4. 其他方法直接转发给原始的 IActivityManager
 * 
 * 注意：
 * 1. 此类必须在应用启动早期调用（在 attachBaseContext 中）
 * 2. 此类不能被混淆
 */
object AMSHookHelper {
    
    private const val TAG = "AMSHookHelper"
    
    /**
     * 是否已经 Hook
     */
    private var isHooked = false
    
    /**
     * Hook AMS
     */
    fun hookAMS(context: Context) {
        if (isHooked) {
            Logger.i(TAG, "AMS already hooked, skip")
            return
        }
        
        try {
            Logger.i(TAG, "Start hooking AMS...")
            
            // 1. 获取 IActivityManager 实例
            val iActivityManager = getIActivityManager()
            if (iActivityManager == null) {
                Logger.e(TAG, "Failed to get IActivityManager")
                return
            }
            
            Logger.d(TAG, "Got IActivityManager: ${iActivityManager.javaClass.name}")
            
            // 2. 创建动态代理
            val proxyAMS = Proxy.newProxyInstance(
                context.classLoader,
                arrayOf(Class.forName("android.app.IActivityManager")),
                AMSInvocationHandler(iActivityManager, context)
            )
            
            Logger.d(TAG, "Created AMS proxy: ${proxyAMS.javaClass.name}")
            
            // 3. 替换系统的 IActivityManager
            replaceIActivityManager(proxyAMS)
            
            isHooked = true
            Logger.i(TAG, "✅ AMS hook successfully")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to hook AMS", e)
        }
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
        return try {
            // 先尝试 Android 8.0+ 的方式
            val activityManagerClass = Class.forName("android.app.ActivityManager")
            val iActivityManagerSingletonField = activityManagerClass.getDeclaredField("IActivityManagerSingleton")
            iActivityManagerSingletonField.isAccessible = true
            val singleton = iActivityManagerSingletonField.get(null)
            
            // 从 Singleton 中获取 IActivityManager
            val singletonClass = Class.forName("android.util.Singleton")
            val getMethod = singletonClass.getDeclaredMethod("get")
            getMethod.isAccessible = true
            getMethod.invoke(singleton)
            
        } catch (e: Exception) {
            Logger.d(TAG, "Failed to get IActivityManager via ActivityManager, try ActivityManagerNative")
            
            // 尝试 Android 8.0 之前的方式
            try {
                val activityManagerNativeClass = Class.forName("android.app.ActivityManagerNative")
                val getDefaultMethod = activityManagerNativeClass.getDeclaredMethod("getDefault")
                getDefaultMethod.isAccessible = true
                getDefaultMethod.invoke(null)
            } catch (e2: Exception) {
                Logger.e(TAG, "Failed to get IActivityManager via ActivityManagerNative", e2)
                null
            }
        }
    }
    
    /**
     * 替换系统的 IActivityManager
     */
    @SuppressLint("PrivateApi")
    private fun replaceIActivityManager(proxy: Any) {
        try {
            // 先尝试 Android 8.0+ 的方式
            val activityManagerClass = Class.forName("android.app.ActivityManager")
            val iActivityManagerSingletonField = activityManagerClass.getDeclaredField("IActivityManagerSingleton")
            iActivityManagerSingletonField.isAccessible = true
            val singleton = iActivityManagerSingletonField.get(null)
            
            // 替换 Singleton 中的 mInstance
            val singletonClass = Class.forName("android.util.Singleton")
            val mInstanceField = singletonClass.getDeclaredField("mInstance")
            mInstanceField.isAccessible = true
            mInstanceField.set(singleton, proxy)
            
            Logger.d(TAG, "Replaced IActivityManager via ActivityManager.IActivityManagerSingleton")
            
        } catch (e: Exception) {
            Logger.d(TAG, "Failed to replace via ActivityManager, try ActivityManagerNative")
            
            // 尝试 Android 8.0 之前的方式
            try {
                val activityManagerNativeClass = Class.forName("android.app.ActivityManagerNative")
                val gDefaultField = activityManagerNativeClass.getDeclaredField("gDefault")
                gDefaultField.isAccessible = true
                val singleton = gDefaultField.get(null)
                
                // 替换 Singleton 中的 mInstance
                val singletonClass = Class.forName("android.util.Singleton")
                val mInstanceField = singletonClass.getDeclaredField("mInstance")
                mInstanceField.isAccessible = true
                mInstanceField.set(singleton, proxy)
                
                Logger.d(TAG, "Replaced IActivityManager via ActivityManagerNative.gDefault")
                
            } catch (e2: Exception) {
                Logger.e(TAG, "Failed to replace IActivityManager", e2)
                throw e2
            }
        }
    }
    
    /**
     * AMS 方法调用处理器
     */
    private class AMSInvocationHandler(
        private val base: Any,
        private val context: Context
    ) : InvocationHandler {
        
        private val TAG = "AMSInvocationHandler"
        
        override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
            // 拦截 startService 方法
            if (method.name == "startService") {
                return handleStartService(method, args)
            }
            
            // 拦截 bindService 方法
            if (method.name == "bindService" || method.name == "bindIsolatedService") {
                return handleBindService(method, args)
            }
            
            // 其他方法直接转发
            return try {
                method.invoke(base, *(args ?: emptyArray()))
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.targetException ?: e
            }
        }
        
        /**
         * 处理 startService 方法
         * 
         * startService 的方法签名（可能因 Android 版本而异）：
         * - ComponentName startService(IApplicationThread caller, Intent service, String resolvedType, ...)
         */
        private fun handleStartService(method: Method, args: Array<out Any?>?): Any? {
            try {
                Logger.d(TAG, "Intercepting startService")
                
                // 处理 Intent：将未注册的 Service 替换为 StubService
                if (args != null && args.size >= 2) {
                    val intent = args[1] as? android.content.Intent
                    if (intent != null) {
                        ServiceHelper.processStartServiceIntent(context, intent)
                    }
                }
                
                // 调用原始方法
                return method.invoke(base, *(args ?: emptyArray()))
                
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.targetException ?: e
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to handle startService", e)
                throw e
            }
        }
        
        /**
         * 处理 bindService 方法
         * 
         * bindService 的方法签名（可能因 Android 版本而异）：
         * - int bindService(IApplicationThread caller, IBinder token, Intent service, String resolvedType, IServiceConnection connection, int flags, ...)
         */
        private fun handleBindService(method: Method, args: Array<out Any?>?): Any? {
            try {
                Logger.d(TAG, "Intercepting bindService")
                
                // 处理 Intent：将未注册的 Service 替换为 StubService
                if (args != null && args.size >= 3) {
                    val intent = args[2] as? android.content.Intent
                    if (intent != null) {
                        ServiceHelper.processBindServiceIntent(context, intent)
                    }
                }
                
                // 调用原始方法
                return method.invoke(base, *(args ?: emptyArray()))
                
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.targetException ?: e
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to handle bindService", e)
                throw e
            }
        }
    }
}

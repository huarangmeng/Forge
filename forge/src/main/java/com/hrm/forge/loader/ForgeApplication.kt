package com.hrm.forge.loader

import android.app.Application
import android.content.Context
import com.hrm.forge.common.DataSavingUtils
import com.hrm.forge.logger.Logger

/**
 * Forge Application 基类
 * 继承此类并实现 getApplicationLike() 方法
 */
abstract class ForgeApplication : Application() {
    
    private val TAG = "ForgeApplication"
    
    companion object {
        private var applicationLikeInstance: Any? = null
        
        fun getApplicationLike(): Any? = applicationLikeInstance
    }
    
    /**
     * 子类需要实现此方法，返回实际 ApplicationLike 的类名
     */
    protected abstract fun getApplicationLike(): String?
    
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        
        // 初始化数据存储
        DataSavingUtils.init(base)
        
        // 加载新版本 APK
        val loadResult = ForgeAllLoader.loadNewApk(
            base,
            getApplicationLike(),
            base.applicationInfo.nativeLibraryDir
        )
        
        Logger.i(TAG, "Load result: $loadResult")
        
        // 调用 ApplicationLike 的 attachBaseContext
        invokeApplicationLikeMethod("attachBaseContext", Context::class.java, base)
    }
    
    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "Application onCreate")
        
        // 调用 ApplicationLike 的 onCreate
        invokeApplicationLikeMethod("onCreate")
    }
    
    override fun onTerminate() {
        super.onTerminate()
        Logger.i(TAG, "Application onTerminate")
        
        // 调用 ApplicationLike 的 onTerminate
        invokeApplicationLikeMethod("onTerminate")
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        Logger.i(TAG, "Application onLowMemory")
        
        // 调用 ApplicationLike 的 onLowMemory
        invokeApplicationLikeMethod("onLowMemory")
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Logger.i(TAG, "Application onTrimMemory: $level")
        
        // 调用 ApplicationLike 的 onTrimMemory
        invokeApplicationLikeMethod("onTrimMemory", Int::class.java, level)
    }
    
    /**
     * 通过反射调用 ApplicationLike 的方法
     */
    private fun invokeApplicationLikeMethod(
        methodName: String,
        parameterType: Class<*>? = null,
        parameter: Any? = null
    ) {
        try {
            val appLike = applicationLikeInstance ?: return
            val clazz = appLike.javaClass
            
            val method = if (parameterType != null) {
                clazz.getMethod(methodName, parameterType)
            } else {
                clazz.getMethod(methodName)
            }
            
            if (parameter != null) {
                method.invoke(appLike, parameter)
            } else {
                method.invoke(appLike)
            }
            
            Logger.d(TAG, "Invoke ApplicationLike.$methodName success")
        } catch (e: Exception) {
            Logger.e(TAG, "Invoke ApplicationLike.$methodName failed", e)
        }
    }
    
    internal fun setApplicationLike(appLike: Any) {
        applicationLikeInstance = appLike
    }
}

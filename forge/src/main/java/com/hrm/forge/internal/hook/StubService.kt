package com.hrm.forge.internal.hook

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.hrm.forge.internal.log.Logger

/**
 * 占坑 Service
 * 
 * 这个 Service 会在运行时被替换为真实的 Service
 * 
 * 工作原理：
 * 1. 系统启动 StubService
 * 2. 在 onCreate 时创建真实的 Service 实例
 * 3. 将所有生命周期方法转发给真实 Service
 * 
 * 注意：
 * 1. 必须在主 APK 的 AndroidManifest.xml 中注册
 * 2. 此类不能被混淆
 */
internal class StubService : Service() {
    
    private val TAG = "StubService"
    
    /**
     * 真实的 Service 实例
     */
    private var realService: Service? = null
    
    /**
     * 真实 Service 的类名
     */
    private var realServiceClassName: String? = null
    
    /**
     * 创建真实的 Service 实例
     */
    private fun createRealService(intent: Intent?): Service? {
        try {
            // 从 Intent 获取真实 Service 类名
            val realClassName = intent?.getStringExtra(ComponentManager.KEY_REAL_SERVICE)
            
            if (realClassName.isNullOrEmpty()) {
                Logger.e(TAG, "Real service class name is null or empty")
                return null
            }
            
            realServiceClassName = realClassName
            Logger.i(TAG, "Creating real service: $realClassName")
            
            // 使用当前的 ClassLoader 加载真实 Service（已被热更新替换）
            val serviceClass = Class.forName(realClassName)
            val service = serviceClass.newInstance() as Service
            
            // 使用反射设置 Service 的必要字段
            injectServiceFields(service)
            
            Logger.i(TAG, "✅ Real service created successfully: $realClassName")
            return service
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create real service", e)
            return null
        }
    }
    
    /**
     * 注入 Service 的必要字段
     * Service 需要一些内部字段才能正常工作
     * 
     * 注意：我们不调用 Service.attach() 方法（这是内部 API），
     * 而是直接复制 StubService 已经初始化好的字段到真实 Service
     */
    private fun injectServiceFields(service: Service) {
        try {
            // 确保 realServiceClassName 非空
            val className = realServiceClassName ?: run {
                Logger.e(TAG, "realServiceClassName is null")
                return
            }
            
            Logger.d(TAG, "Start injecting service fields for: $className")
            
            // 获取 Service 的内部字段并设置
            // 这些字段是 Service 正常工作所必需的
            
            // 1. mThread - ActivityThread 实例
            val activityThreadField = Service::class.java.getDeclaredField("mThread")
            activityThreadField.isAccessible = true
            val activityThread = activityThreadField.get(this)
            activityThreadField.set(service, activityThread)
            Logger.d(TAG, "✓ mThread injected")
            
            // 2. mToken - Service 的 Binder token
            val activityTokenField = Service::class.java.getDeclaredField("mToken")
            activityTokenField.isAccessible = true
            val activityToken = activityTokenField.get(this)
            activityTokenField.set(service, activityToken)
            Logger.d(TAG, "✓ mToken injected")
            
            // 3. mApplication - Application 实例
            val applicationField = Service::class.java.getDeclaredField("mApplication")
            applicationField.isAccessible = true
            applicationField.set(service, application)
            Logger.d(TAG, "✓ mApplication injected")
            
            // 4. mClassName - Service 类名（使用真实类名）
            val classNameField = Service::class.java.getDeclaredField("mClassName")
            classNameField.isAccessible = true
            classNameField.set(service, className)
            Logger.d(TAG, "✓ mClassName injected: $className")
            
            // 5. mActivityManager - IActivityManager 实例
            val activityManagerField = Service::class.java.getDeclaredField("mActivityManager")
            activityManagerField.isAccessible = true
            val activityManager = activityManagerField.get(this)
            activityManagerField.set(service, activityManager)
            Logger.d(TAG, "✓ mActivityManager injected")
            
            // 6. 其他可能需要的字段
            try {
                val startCompatibilityField = Service::class.java.getDeclaredField("mStartCompatibility")
                startCompatibilityField.isAccessible = true
                val startCompatibility = startCompatibilityField.get(this)
                startCompatibilityField.set(service, startCompatibility)
                Logger.d(TAG, "✓ mStartCompatibility injected")
            } catch (e: NoSuchFieldException) {
                // 某些 Android 版本可能没有这个字段，忽略
                Logger.d(TAG, "mStartCompatibility field not found, skip")
            }
            
            Logger.i(TAG, "✅ Service fields injected successfully for: $className")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to inject service fields", e)
        }
    }
    
    override fun onCreate() {
        Logger.d(TAG, "StubService onCreate")
        // 不调用 super.onCreate()，因为我们要完全由真实 Service 控制
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.d(TAG, "StubService onStartCommand")
        
        // 如果真实 Service 还未创建，先创建
        if (realService == null) {
            realService = createRealService(intent)
            realService?.onCreate()
        }
        
        // 转发给真实 Service
        return realService?.onStartCommand(intent, flags, startId) ?: START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        Logger.d(TAG, "StubService onBind")
        
        // 如果真实 Service 还未创建，先创建
        if (realService == null) {
            realService = createRealService(intent)
            realService?.onCreate()
        }
        
        // 转发给真实 Service
        return realService?.onBind(intent)
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        Logger.d(TAG, "StubService onUnbind")
        return realService?.onUnbind(intent) ?: false
    }
    
    override fun onRebind(intent: Intent?) {
        Logger.d(TAG, "StubService onRebind")
        realService?.onRebind(intent)
    }
    
    override fun onDestroy() {
        Logger.d(TAG, "StubService onDestroy")
        realService?.onDestroy()
        realService = null
        super.onDestroy()
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        realService?.onConfigurationChanged(newConfig)
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        realService?.onLowMemory()
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        realService?.onTrimMemory(level)
    }
}

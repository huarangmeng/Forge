package com.hrm.forge.loader

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import com.hrm.forge.logger.Logger
import java.lang.reflect.Method

/**
 * 资源动态加载器
 * 通过反射替换 AssetManager 实现资源热更新
 */
object ForgeResourceLoader {
    private const val TAG = "ForgeResourceLoader"
    
    @Volatile
    private var newResources: Resources? = null
    
    /**
     * 加载新的资源
     * @param context Context
     * @param apkPath APK 路径
     */
    @SuppressLint("PrivateApi")
    fun loadResources(context: Context, apkPath: String) {
        Logger.i(TAG, "Start load resources from: $apkPath")
        
        val startTime = System.currentTimeMillis()
        
        try {
            // 创建新的 AssetManager
            val assetManager = AssetManager::class.java.newInstance()
            
            // 添加资源路径
            val addAssetPathMethod = AssetManager::class.java.getDeclaredMethod(
                "addAssetPath",
                String::class.java
            )
            addAssetPathMethod.isAccessible = true
            val cookie = addAssetPathMethod.invoke(assetManager, apkPath) as? Int
            
            if (cookie == null || cookie == 0) {
                throw RuntimeException("addAssetPath failed, cookie is 0")
            }
            
            Logger.d(TAG, "addAssetPath success, cookie: $cookie")
            
            // 创建新的 Resources 对象
            val resources = context.resources
            newResources = Resources(
                assetManager,
                resources.displayMetrics,
                resources.configuration
            )
            
            // 替换 ContextImpl 中的 Resources
            replaceContextResources(context, newResources!!)
            
            // 替换 LoadedApk 中的 Resources
            replaceLoadedApkResources(context, newResources!!)
            
            val elapsed = System.currentTimeMillis() - startTime
            Logger.i(TAG, "Load resources success, elapsed: ${elapsed}ms")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Load resources failed", e)
            throw e
        }
    }
    
    /**
     * 替换 ContextImpl 中的 Resources
     */
    private fun replaceContextResources(context: Context, resources: Resources) {
        try {
            // 获取 ContextImpl
            val contextImplClass = Class.forName("android.app.ContextImpl")
            
            // 替换 mResources
            val mResourcesField = contextImplClass.getDeclaredField("mResources")
            mResourcesField.isAccessible = true
            mResourcesField.set(context, resources)
            
            Logger.d(TAG, "Replace ContextImpl resources success")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Replace ContextImpl resources failed", e)
            throw e
        }
    }
    
    /**
     * 替换 LoadedApk 中的 Resources
     */
    @SuppressLint("PrivateApi")
    private fun replaceLoadedApkResources(context: Context, resources: Resources) {
        try {
            // 获取 ContextImpl 的 mPackageInfo 字段
            val contextImplClass = Class.forName("android.app.ContextImpl")
            val mPackageInfoField = contextImplClass.getDeclaredField("mPackageInfo")
            mPackageInfoField.isAccessible = true
            val loadedApk = mPackageInfoField.get(context)
            
            if (loadedApk != null) {
                // 替换 LoadedApk 的 mResources
                val loadedApkClass = loadedApk.javaClass
                val mResourcesField = loadedApkClass.getDeclaredField("mResources")
                mResourcesField.isAccessible = true
                mResourcesField.set(loadedApk, resources)
                
                Logger.d(TAG, "Replace LoadedApk resources success")
            }
            
        } catch (e: Exception) {
            Logger.e(TAG, "Replace LoadedApk resources failed", e)
            // 不抛出异常，因为这不是致命错误
        }
    }
    
    /**
     * 替换 ActivityThread 中的所有 Resources
     * 这个方法在某些场景下可能需要，比如多个 Activity 共享资源
     */
    @SuppressLint("PrivateApi")
    fun replaceActivityThreadResources(resources: Resources) {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            
            // 获取 currentActivityThread
            val currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread")
            currentActivityThreadMethod.isAccessible = true
            val activityThread = currentActivityThreadMethod.invoke(null)
            
            // 获取 mResourcesManager
            val mResourcesManagerField = activityThreadClass.getDeclaredField("mResourcesManager")
            mResourcesManagerField.isAccessible = true
            val resourcesManager = mResourcesManagerField.get(activityThread)
            
            if (resourcesManager != null) {
                // 替换 ResourcesManager 中的 Resources
                replaceResourcesManager(resourcesManager, resources)
            }
            
            Logger.d(TAG, "Replace ActivityThread resources success")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Replace ActivityThread resources failed", e)
        }
    }
    
    /**
     * 替换 ResourcesManager 中的 Resources
     */
    private fun replaceResourcesManager(resourcesManager: Any, resources: Resources) {
        try {
            val resourcesManagerClass = resourcesManager.javaClass
            val mActiveResourcesField = resourcesManagerClass.getDeclaredField("mActiveResources")
            mActiveResourcesField.isAccessible = true
            val activeResources = mActiveResourcesField.get(resourcesManager) as? MutableMap<*, *>

            if (activeResources != null) {
                @Suppress("UNCHECKED_CAST")
                val map = activeResources as MutableMap<Any, Any>

                // 替换所有的 Resources 引用
                map.forEach { (key, value) ->
                    try {
                        // Resources 可能包装在 WeakReference 中
                        if (value is java.lang.ref.WeakReference<*>) {
                            val ref = value
                            if (ref.get() != null) {
                                map[key] = java.lang.ref.WeakReference(resources)
                            }
                        } else if (value is Resources) {
                            map[key] = resources
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, "Replace resource entry failed", e)
                    }
                }

                Logger.d(TAG, "Replace ResourcesManager active resources success")
            }

        } catch (e: Exception) {
            Logger.e(TAG, "Replace ResourcesManager failed", e)
        }
    }
    
    /**
     * 获取新的 Resources 对象
     */
    fun getNewResources(): Resources? = newResources
}

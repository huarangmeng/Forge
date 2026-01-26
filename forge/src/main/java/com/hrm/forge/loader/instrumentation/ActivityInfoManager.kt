package com.hrm.forge.loader.instrumentation

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import com.hrm.forge.logger.Logger
import java.io.File

/**
 * Activity 信息管理器
 * 
 * 负责：
 * 1. 解析主 APK 和热更新 APK 的 Activity 信息
 * 2. 根据启动模式选择合适的占坑 Activity
 * 3. 管理 Activity 映射关系
 */
object ActivityInfoManager {
    
    private const val TAG = "ActivityInfoManager"
    
    // 占坑 Activity 映射：launchMode -> 占坑 Activity 类名
    private val STUB_ACTIVITIES = mapOf(
        ActivityInfo.LAUNCH_MULTIPLE to "com.hrm.forge.loader.instrumentation.StubActivityStandard",
        ActivityInfo.LAUNCH_SINGLE_TOP to "com.hrm.forge.loader.instrumentation.StubActivitySingleTop",
        ActivityInfo.LAUNCH_SINGLE_TASK to "com.hrm.forge.loader.instrumentation.StubActivitySingleTask",
        ActivityInfo.LAUNCH_SINGLE_INSTANCE to "com.hrm.forge.loader.instrumentation.StubActivitySingleInstance"
    )
    
    // 主 APK 中已注册的 Activity：className -> launchMode
    private val mainActivities = mutableMapOf<String, Int>()
    
    // 热更新 APK 中的 Activity：className -> launchMode
    private val hotUpdateActivities = mutableMapOf<String, Int>()
    
    /**
     * 初始化 Activity 信息
     * 
     * @param context 上下文
     * @param hotUpdateApkPath 热更新 APK 路径
     */
    fun init(context: Context, hotUpdateApkPath: String?) {
        Logger.i(TAG, "Initializing ActivityInfoManager")
        
        try {
            // 1. 解析主 APK 的 Activity 信息
            parseMainActivities(context)
            
            // 2. 解析热更新 APK 的 Activity 信息
            if (hotUpdateApkPath != null && File(hotUpdateApkPath).exists()) {
                parseHotUpdateActivities(context, hotUpdateApkPath)
            }
            
            Logger.i(TAG, "ActivityInfoManager initialized successfully")
            Logger.i(TAG, "Main activities: ${mainActivities.size}, Hot update activities: ${hotUpdateActivities.size}")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize ActivityInfoManager", e)
        }
    }
    
    /**
     * 解析主 APK 的 Activity 信息
     */
    private fun parseMainActivities(context: Context) {
        try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
            
            packageInfo.activities?.forEach { activityInfo ->
                mainActivities[activityInfo.name] = activityInfo.launchMode
                Logger.d(TAG, "Main activity: ${activityInfo.name}, launchMode: ${activityInfo.launchMode}")
            }
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse main activities", e)
        }
    }
    
    /**
     * 解析热更新 APK 的 Activity 信息
     */
    private fun parseHotUpdateActivities(context: Context, apkPath: String) {
        try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES)
            
            packageInfo?.activities?.forEach { activityInfo ->
                // 只记录不在主 APK 中的 Activity
                if (!mainActivities.containsKey(activityInfo.name)) {
                    hotUpdateActivities[activityInfo.name] = activityInfo.launchMode
                    Logger.d(TAG, "Hot update activity: ${activityInfo.name}, launchMode: ${activityInfo.launchMode}")
                }
            }
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse hot update activities", e)
        }
    }
    
    /**
     * 检查 Activity 是否在主 APK 中注册
     */
    fun isActivityRegisteredInMain(activityClassName: String): Boolean {
        return mainActivities.containsKey(activityClassName)
    }
    
    /**
     * 获取 Activity 的启动模式
     */
    fun getActivityLaunchMode(activityClassName: String): Int {
        // 优先从热更新 APK 中查找
        hotUpdateActivities[activityClassName]?.let { return it }
        
        // 如果热更新中没有，从主 APK 中查找
        mainActivities[activityClassName]?.let { return it }
        
        // 默认返回 standard 模式
        return ActivityInfo.LAUNCH_MULTIPLE
    }
    
    /**
     * 根据启动模式获取对应的占坑 Activity
     */
    fun getStubActivityForLaunchMode(launchMode: Int): String {
        return STUB_ACTIVITIES[launchMode] ?: STUB_ACTIVITIES[ActivityInfo.LAUNCH_MULTIPLE]!!
    }
    
    /**
     * 获取真实 Activity 对应的占坑 Activity
     */
    fun getStubActivityForRealActivity(activityClassName: String): String {
        val launchMode = getActivityLaunchMode(activityClassName)
        return getStubActivityForLaunchMode(launchMode)
    }
    
    /**
     * 清除所有数据
     */
    fun clear() {
        mainActivities.clear()
        hotUpdateActivities.clear()
        Logger.i(TAG, "ActivityInfoManager cleared")
    }
}

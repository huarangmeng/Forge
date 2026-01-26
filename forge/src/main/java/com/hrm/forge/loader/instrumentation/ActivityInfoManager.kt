package com.hrm.forge.loader.instrumentation

import android.content.Context

/**
 * Activity 信息管理器（兼容层）
 * 
 * 为了保持向后兼容，将所有方法委托给 ComponentInfoManager
 * 
 * @deprecated 建议直接使用 ComponentInfoManager
 */
object ActivityInfoManager {
    
    /**
     * 初始化 Activity 信息
     */
    fun init(context: Context, hotUpdateApkPath: String?) {
        ComponentInfoManager.init(context, hotUpdateApkPath)
    }
    
    /**
     * 检查 Activity 是否在主 APK 中注册
     */
    fun isActivityRegisteredInMain(activityClassName: String): Boolean {
        return ComponentInfoManager.isActivityRegisteredInMain(activityClassName)
    }
    
    /**
     * 检查 Activity 是否在热更新 APK 中存在
     */
    fun isActivityInHotUpdate(activityClassName: String): Boolean {
        return ComponentInfoManager.isActivityInHotUpdate(activityClassName)
    }
    
    /**
     * 检查 Activity 是否存在（主 APK 或热更新 APK）
     */
    fun isActivityExists(activityClassName: String): Boolean {
        return ComponentInfoManager.isActivityExists(activityClassName)
    }
    
    /**
     * 获取 Activity 的启动模式
     */
    fun getActivityLaunchMode(activityClassName: String): Int {
        return ComponentInfoManager.getActivityLaunchMode(activityClassName)
    }
    
    /**
     * 根据启动模式获取对应的占坑 Activity
     */
    fun getStubActivityForLaunchMode(launchMode: Int): String {
        return ComponentInfoManager.getStubActivityForLaunchMode(launchMode)
    }
    
    /**
     * 获取真实 Activity 对应的占坑 Activity
     */
    fun getStubActivityForRealActivity(activityClassName: String): String {
        return ComponentInfoManager.getStubActivityForRealActivity(activityClassName)
    }
    
    /**
     * 清除所有数据
     */
    fun clear() {
        ComponentInfoManager.clear()
    }
}

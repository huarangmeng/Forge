package com.hrm.forge.api

/**
 * 版本信息数据类
 * 
 * 这是 Forge SDK 的公开 API，用于查询当前的版本状态
 */
data class VersionInfo(
    val baseVersion: String,                // 基础版本（应用名 + 基础版本号）
    val baseVersionCode: Long,              // 基础版本号
    val currentVersion: String,             // 当前运行版本（应用名 + 版本号）
    val currentVersionCode: Long,           // 当前运行版本号
    val nextVersion: String,                // 下次启动版本（应用名 + 版本号）
    val nextVersionCode: Long,              // 下次启动版本号
    val isHotUpdateLoaded: Boolean,         // 运行时是否已加载热更新
    val hasPendingChange: Boolean,          // 是否有待生效的更改（需要重启）
    val canRollback: Boolean,               // 是否可以回滚
    val apkPath: String?,                   // APK 路径
    val sha1: String?,                      // SHA1 校验值
    val buildNumber: Long?                  // 构建号
)

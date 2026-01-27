package com.hrm.forge.common

/**
 * 全局常量定义
 */
object Constants {
    // SharedPreferences Keys - 版本管理
    const val KEY_CURRENT_VERSION = "forge_current_version"
    const val KEY_VERSION_SHA1 = "forge_version_sha1"
    const val KEY_PREVIOUS_VERSION = "forge_previous_version"
    const val KEY_SO_FILES_SHA1 = "forge_so_files_sha1"
    const val KEY_CURRENT_BUILD_NUMBER = "forge_current_build_number"
    const val KEY_CURRENT_SHA1 = "forge_current_sha1"
    const val KEY_LOAD_SUCCESS = "forge_load_success"
    
    // File Operations
    const val FILE_BUFFER_SIZE = 64 * 1024 // 64KB
    const val MAX_VERSION_RETENTION = 2
    
    // Directory Names
    const val DIR_FORGE = "forge"
    const val DIR_VERSIONS = "versions"
    const val DIR_SO = "so"
    
    // SO ABI (仅支持 ARM 64位)
    const val SUPPORTED_ABI = "arm64-v8a"
}

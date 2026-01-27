package com.hrm.forge.api

/**
 * 版本发布结果
 * 
 * 用于区分版本发布过程中的各个环节和错误类型
 */
enum class ReleaseResult(val code: Int, val message: String) {
    // 成功
    SUCCESS(0, "发布成功"),
    
    // APK 验证失败
    APK_NOT_FOUND(1001, "APK 文件不存在"),
    APK_INVALID_FORMAT(1002, "APK 格式无效"),
    APK_READ_VERSION_FAILED(1003, "读取 APK 版本信息失败"),
    APK_PACKAGE_MISMATCH(1004, "APK 包名不匹配"),
    APK_SIGNATURE_MISMATCH(1005, "APK 签名不匹配"),
    APK_VERSION_CODE_INVALID(1006, "APK 版本码无效"),
    
    // APK 安装失败（复制到版本目录）
    INSTALL_CREATE_DIR_FAILED(2001, "创建版本目录失败"),
    INSTALL_COPY_APK_FAILED(2002, "复制 APK 文件失败"),
    INSTALL_CALCULATE_SHA1_FAILED(2003, "计算 APK SHA1 失败"),
    
    // APK 优化失败（非致命错误，通常会继续）
    OPTIMIZE_UNZIP_FAILED(3001, "解压 APK 失败"),
    OPTIMIZE_DEX_PRELOAD_FAILED(3002, "DEX 预加载失败"),
    
    // 版本状态保存失败
    SAVE_VERSION_STATE_FAILED(4001, "保存版本状态失败"),
    
    // 清理旧版本失败（非致命错误）
    CLEAN_OLD_VERSIONS_FAILED(5001, "清理旧版本失败"),
    
    // 未知错误
    UNKNOWN_ERROR(9999, "未知错误");
    
    /**
     * 是否成功
     */
    val isSuccess: Boolean
        get() = this == SUCCESS
    
    /**
     * 是否失败
     */
    val isFailure: Boolean
        get() = !isSuccess
}

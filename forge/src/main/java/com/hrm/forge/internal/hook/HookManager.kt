package com.hrm.forge.internal.hook

import android.content.Context
import com.hrm.forge.internal.hook.base.BaseHook
import com.hrm.forge.internal.log.Logger

/**
 * Hook 管理器
 * 
 * 统一管理所有 Hook 的生命周期和执行顺序
 * 
 * 设计原则：
 * 1. 单一入口：所有 Hook 通过 HookManager 统一管理
 * 2. 有序执行：按照依赖关系确定 Hook 顺序
 * 3. 异常隔离：单个 Hook 失败不影响其他 Hook
 * 4. 统一日志：集中记录所有 Hook 的执行状态
 * 
 * Hook 执行顺序：
 * 1. InstrumentationHook（最早，拦截 Activity 启动）
 * 2. AMSHook（拦截 Service 和 Receiver）
 * 3. ContentProviderHook（安装 ContentProvider）
 */
object HookManager {
    
    private const val TAG = "HookManager"
    
    /**
     * 所有 Hook 实例（按执行顺序）
     */
    private val hooks = mutableListOf<HookEntry>()
    
    /**
     * 是否已初始化
     */
    @Volatile
    private var isInitialized = false
    
    /**
     * Hook 条目
     */
    private data class HookEntry(
        val name: String,
        val hook: BaseHook,
        val required: Boolean = true,  // 是否必须成功
        val hookAction: () -> Unit     // Hook 执行动作
    )
    
    /**
     * 初始化所有 Hook
     * 
     * @param context 应用上下文
     * @param hotUpdateApkPath 热更新 APK 路径（可选）
     */
    fun init(context: Context, hotUpdateApkPath: String? = null) {
        if (isInitialized) {
            Logger.i(TAG, "HookManager already initialized, skip")
            return
        }
        
        try {
            Logger.i(TAG, "=".repeat(50))
            Logger.i(TAG, "Initializing HookManager...")
            Logger.i(TAG, "=".repeat(50))
            
            val startTime = System.currentTimeMillis()
            
            // 1. 初始化 ComponentManager（必须在所有 Hook 之前）
            ComponentManager.init(context, hotUpdateApkPath)
            
            // 2. 注册所有 Hook（按执行顺序）
            registerHooks(context)
            
            // 3. 执行所有 Hook
            executeHooks()
            
            val elapsedTime = System.currentTimeMillis() - startTime
            
            isInitialized = true
            Logger.i(TAG, "=".repeat(50))
            Logger.i(TAG, "✅ HookManager initialized successfully in ${elapsedTime}ms")
            Logger.i(TAG, "=".repeat(50))
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize HookManager", e)
            throw e
        }
    }
    
    /**
     * 注册所有 Hook
     */
    private fun registerHooks(context: Context) {
        hooks.clear()
        
        // Hook 1: InstrumentationHook（拦截 Activity 启动）
        hooks.add(HookEntry(
            name = "Instrumentation",
            hook = InstrumentationHook,
            required = true,
            hookAction = { InstrumentationHook.hookInstrumentation() }
        ))
        
        // Hook 2: AMSHook（拦截 Service 和 Receiver）
        hooks.add(HookEntry(
            name = "AMS",
            hook = AMSHook,
            required = true,
            hookAction = { AMSHook.hookAMS(context) }
        ))
        
        // Hook 3: ContentProviderHook（安装 ContentProvider）
        hooks.add(HookEntry(
            name = "ContentProvider",
            hook = ContentProviderHook,
            required = false,  // ContentProvider 不是必须的
            hookAction = { ContentProviderHook.hook(context) }
        ))
        
        Logger.i(TAG, "Registered ${hooks.size} hooks")
    }
    
    /**
     * 执行所有 Hook
     */
    private fun executeHooks() {
        val results = mutableListOf<HookResult>()
        
        hooks.forEach { entry ->
            val result = executeHook(entry)
            results.add(result)
        }
        
        // 打印执行摘要
        printSummary(results)
        
        // 检查是否有必需的 Hook 失败
        val failedRequired = results.filter { it.required && !it.success }
        if (failedRequired.isNotEmpty()) {
            val failedNames = failedRequired.joinToString(", ") { it.name }
            throw IllegalStateException("Required hooks failed: $failedNames")
        }
    }
    
    /**
     * 执行单个 Hook
     */
    private fun executeHook(entry: HookEntry): HookResult {
        return try {
            Logger.i(TAG, "-".repeat(50))
            Logger.i(TAG, "Executing ${entry.name} Hook...")
            Logger.i(TAG, "-".repeat(50))
            
            val startTime = System.currentTimeMillis()
            entry.hookAction()
            val elapsedTime = System.currentTimeMillis() - startTime
            
            HookResult(
                name = entry.name,
                success = true,
                required = entry.required,
                elapsedTime = elapsedTime
            )
            
        } catch (e: Exception) {
            Logger.e(TAG, "${entry.name} Hook failed", e)
            
            if (entry.required) {
                // 必需的 Hook 失败，记录错误但继续执行其他 Hook
                Logger.e(TAG, "⚠️ Required hook ${entry.name} failed, but continuing...")
            }
            
            HookResult(
                name = entry.name,
                success = false,
                required = entry.required,
                error = e
            )
        }
    }
    
    /**
     * 打印执行摘要
     */
    private fun printSummary(results: List<HookResult>) {
        Logger.i(TAG, "=".repeat(50))
        Logger.i(TAG, "Hook Execution Summary:")
        Logger.i(TAG, "=".repeat(50))
        
        results.forEach { result ->
            val status = if (result.success) "✅" else "❌"
            val required = if (result.required) "[Required]" else "[Optional]"
            val time = result.elapsedTime?.let { "${it}ms" } ?: "N/A"
            
            Logger.i(TAG, "$status ${result.name} $required - $time")
            
            if (!result.success && result.error != null) {
                Logger.e(TAG, "   └─ Error: ${result.error.message}")
            }
        }
        
        val successCount = results.count { it.success }
        val totalCount = results.size
        Logger.i(TAG, "=".repeat(50))
        Logger.i(TAG, "Total: $successCount/$totalCount hooks succeeded")
        Logger.i(TAG, "=".repeat(50))
    }
    
    /**
     * 取消所有 Hook（测试用）
     */
    fun unhook() {
        Logger.i(TAG, "Unhooking all hooks...")
        
        hooks.reversed().forEach { entry ->
            try {
                entry.hook.unhook()
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to unhook ${entry.name}", e)
            }
        }
        
        hooks.clear()
        isInitialized = false
        Logger.i(TAG, "All hooks unhooked")
    }
    
    /**
     * 重置状态（测试用）
     */
    fun reset() {
        hooks.forEach { it.hook.reset() }
        hooks.clear()
        isInitialized = false
        ComponentManager.clear()
        Logger.i(TAG, "HookManager reset")
    }
    
    /**
     * 获取所有 Hook 的状态
     */
    fun getStatus(): String {
        return buildString {
            appendLine("HookManager Status:")
            appendLine("  Initialized: $isInitialized")
            appendLine("  Registered Hooks: ${hooks.size}")
            hooks.forEach { entry ->
                val status = if (entry.hook.isHooked) "✅" else "❌"
                appendLine("    $status ${entry.name}")
            }
        }
    }
    
    /**
     * Hook 执行结果
     */
    private data class HookResult(
        val name: String,
        val success: Boolean,
        val required: Boolean,
        val elapsedTime: Long? = null,
        val error: Exception? = null
    )
}

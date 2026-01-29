package com.hrm.forge.internal.hook.base

import com.hrm.forge.internal.log.Logger

/**
 * Hook 基类
 * 
 * 提供通用的 Hook 生命周期管理和状态控制
 * 
 * 设计原则：
 * 1. 模板方法模式：定义 Hook 的标准流程
 * 2. 单一职责：每个子类只负责一个 Hook 点
 * 3. 防重复 Hook：自动检查状态
 */
abstract class BaseHook {
    
    protected abstract val tag: String
    
    /**
     * Hook 状态
     */
    @Volatile
    private var _isHooked = false
    
    val isHooked: Boolean
        get() = _isHooked
    
    /**
     * 执行 Hook（模板方法）
     * 
     * 自动处理：
     * - 重复 Hook 检查
     * - 异常处理
     * - 状态更新
     * - 日志记录
     */
    fun hook() {
        if (_isHooked) {
            Logger.i(tag, "$tag already hooked, skip")
            return
        }
        
        try {
            Logger.i(tag, "Start hooking $tag...")
            
            // 执行具体的 Hook 逻辑（由子类实现）
            doHook()
            
            _isHooked = true
            Logger.i(tag, "✅ $tag hooked successfully")
            
        } catch (e: Exception) {
            Logger.e(tag, "Failed to hook $tag", e)
            throw HookException("Failed to hook $tag", e)
        }
    }
    
    /**
     * 取消 Hook（可选）
     */
    open fun unhook() {
        if (!_isHooked) {
            Logger.i(tag, "$tag not hooked, skip unhook")
            return
        }
        
        try {
            Logger.i(tag, "Start unhooking $tag...")
            
            doUnhook()
            
            _isHooked = false
            Logger.i(tag, "✅ $tag unhooked successfully")
            
        } catch (e: Exception) {
            Logger.e(tag, "Failed to unhook $tag", e)
            throw HookException("Failed to unhook $tag", e)
        }
    }
    
    /**
     * 重置状态（测试用）
     */
    fun reset() {
        _isHooked = false
        Logger.d(tag, "$tag reset")
    }
    
    /**
     * 子类实现具体的 Hook 逻辑
     */
    protected abstract fun doHook()
    
    /**
     * 子类实现具体的 Unhook 逻辑（可选）
     */
    protected open fun doUnhook() {
        Logger.w(tag, "doUnhook not implemented for $tag")
    }
}

/**
 * Hook 异常
 */
class HookException(message: String, cause: Throwable? = null) : Exception(message, cause)

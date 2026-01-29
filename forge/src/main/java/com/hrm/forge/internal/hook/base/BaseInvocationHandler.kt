package com.hrm.forge.internal.hook.base

import android.content.Intent
import com.hrm.forge.internal.log.Logger
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * 基础的动态代理 InvocationHandler
 * 
 * 提供通用的方法拦截和转发逻辑，子类只需：
 * 1. 实现拦截规则（shouldIntercept）
 * 2. 实现拦截处理（handleIntercept）
 * 
 * 特性：
 * - 自动处理 InvocationTargetException
 * - 统一日志记录
 * - 支持 Before/After 钩子
 * - Intent 参数自动识别
 */
abstract class BaseInvocationHandler(
    protected val base: Any,
    protected val tag: String = "BaseInvocationHandler"
) : InvocationHandler {
    
    override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
        try {
            // 1. 检查是否需要拦截
            if (shouldIntercept(method, args)) {
                Logger.d(tag, "Intercepting ${method.name}")
                
                // 2. 执行拦截处理
                val result = handleIntercept(method, args)
                
                // 3. 如果拦截处理返回非空，直接返回（不调用原方法）
                if (result != INVOKE_ORIGINAL) {
                    return result
                }
            }
            
            // 4. 调用原始方法
            return invokeOriginal(method, args)
            
        } catch (e: InvocationTargetException) {
            // 解包 InvocationTargetException，抛出真实异常
            throw e.targetException ?: e
        } catch (e: Exception) {
            Logger.e(tag, "Failed to invoke ${method.name}", e)
            throw e
        }
    }
    
    /**
     * 判断是否需要拦截该方法
     * 
     * @return true 表示需要拦截，false 表示直接转发
     */
    protected abstract fun shouldIntercept(method: Method, args: Array<out Any?>?): Boolean
    
    /**
     * 处理拦截的方法
     * 
     * @return 返回方法的结果，或 INVOKE_ORIGINAL 表示继续调用原方法
     */
    protected abstract fun handleIntercept(method: Method, args: Array<out Any?>?): Any?
    
    /**
     * 调用原始方法
     */
    protected open fun invokeOriginal(method: Method, args: Array<out Any?>?): Any? {
        return method.invoke(base, *(args ?: emptyArray()))
    }
    
    /**
     * 从参数中查找 Intent
     * 
     * 常用于拦截 startService、bindService、broadcastIntent 等方法
     * 
     * @param args 方法参数数组
     * @return Intent 对象，如果未找到则返回 null
     */
    protected fun findIntent(args: Array<out Any?>?): Intent? {
        return args?.firstOrNull { it is Intent } as? Intent
    }
    
    /**
     * 从参数中查找指定索引的 Intent
     */
    protected fun getIntent(args: Array<out Any?>?, index: Int): Intent? {
        return args?.getOrNull(index) as? Intent
    }
    
    companion object {
        /**
         * 特殊返回值：表示需要继续调用原方法
         */
        val INVOKE_ORIGINAL = Any()
    }
}

/**
 * 方法名匹配的 InvocationHandler（便捷类）
 * 
 * 适用于只需要拦截特定方法名的场景
 */
abstract class MethodNameInvocationHandler(
    base: Any,
    tag: String = "MethodNameInvocationHandler",
    private val interceptMethods: Set<String>
) : BaseInvocationHandler(base, tag) {
    
    constructor(base: Any, tag: String, vararg methodNames: String) : this(
        base, tag, methodNames.toSet()
    )
    
    override fun shouldIntercept(method: Method, args: Array<out Any?>?): Boolean {
        return method.name in interceptMethods
    }
}

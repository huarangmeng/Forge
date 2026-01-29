package com.hrm.forge.internal.util

import android.annotation.SuppressLint
import com.hrm.forge.internal.log.Logger
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 反射工具类
 *
 * 统一管理反射操作，提供：
 * - 类型安全的反射方法
 * - 自动异常处理
 * - 日志记录
 * - 缓存机制（可选）
 */
object ReflectUtil {

    private const val TAG = "ReflectUtil"

    /**
     * 获取类
     */
    @SuppressLint("PrivateApi")
    fun getClass(className: String): Class<*>? {
        return try {
            Class.forName(className)
        } catch (e: ClassNotFoundException) {
            Logger.w(TAG, "Class not found: $className", e)
            null
        }
    }

    /**
     * 获取字段
     *
     * @param clazz 目标类
     * @param fieldName 字段名
     * @param makeAccessible 是否设置为可访问
     */
    fun getField(clazz: Class<*>, fieldName: String, makeAccessible: Boolean = true): Field? {
        return try {
            clazz.getDeclaredField(fieldName).apply {
                if (makeAccessible) {
                    isAccessible = true
                }
            }
        } catch (e: NoSuchFieldException) {
            Logger.w(TAG, "Field not found: ${clazz.name}.$fieldName", e)
            null
        }
    }

    /**
     * 获取字段值
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getFieldValue(obj: Any?, clazz: Class<*>, fieldName: String): T? {
        return try {
            val field = getField(clazz, fieldName) ?: return null
            field.get(obj) as? T
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to get field value: ${clazz.name}.$fieldName", e)
            null
        }
    }

    /**
     * 设置字段值
     */
    fun setFieldValue(obj: Any?, clazz: Class<*>, fieldName: String, value: Any?): Boolean {
        return try {
            val field = getField(clazz, fieldName) ?: return false
            field.set(obj, value)
            true
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to set field value: ${clazz.name}.$fieldName", e)
            false
        }
    }

    /**
     * 获取方法
     *
     * @param clazz 目标类
     * @param methodName 方法名
     * @param parameterTypes 参数类型
     * @param makeAccessible 是否设置为可访问
     */
    fun getMethod(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>,
        makeAccessible: Boolean = true
    ): Method? {
        return try {
            clazz.getDeclaredMethod(methodName, *parameterTypes).apply {
                if (makeAccessible) {
                    isAccessible = true
                }
            }
        } catch (e: NoSuchMethodException) {
            Logger.w(TAG, "Method not found: ${clazz.name}.$methodName", e)
            null
        }
    }

    /**
     * 调用方法
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> invokeMethod(
        obj: Any?,
        clazz: Class<*>,
        methodName: String,
        vararg args: Any?
    ): T? {
        return try {
            val parameterTypes = args.map { it?.javaClass ?: Any::class.java }.toTypedArray()
            val method = getMethod(clazz, methodName, *parameterTypes) ?: return null
            method.invoke(obj, *args) as? T
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to invoke method: ${clazz.name}.$methodName", e)
            null
        }
    }

    /**
     * 获取静态字段值（便捷方法）
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getStaticFieldValue(clazz: Class<*>, fieldName: String): T? {
        return getFieldValue(null, clazz, fieldName)
    }

    /**
     * 设置静态字段值（便捷方法）
     */
    fun setStaticFieldValue(clazz: Class<*>, fieldName: String, value: Any?): Boolean {
        return setFieldValue(null, clazz, fieldName, value)
    }

    /**
     * 调用静态方法（便捷方法）
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> invokeStaticMethod(clazz: Class<*>, methodName: String, vararg args: Any?): T? {
        return invokeMethod(null, clazz, methodName, *args)
    }

    /**
     * 获取 Singleton 实例
     *
     * Android 系统常用的单例模式：
     * - ActivityManager.IActivityManagerSingleton
     * - ActivityManagerNative.gDefault
     *
     * @param singletonClass 单例包装类
     * @param singletonFieldName 单例字段名
     * @param instanceFieldName Singleton 中 mInstance 字段名
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getSingletonInstance(
        singletonClass: Class<*>,
        singletonFieldName: String,
        instanceFieldName: String = "mInstance"
    ): T? {
        return try {
            // 1. 获取 Singleton 对象
            val singleton = getStaticFieldValue<Any>(singletonClass, singletonFieldName)
                ?: return null

            // 2. 获取 Singleton 的 Class
            val singletonClazz = Class.forName("android.util.Singleton")

            // 3. 调用 get() 方法或直接访问 mInstance
            getFieldValue<T>(singleton, singletonClazz, instanceFieldName)
                ?: invokeMethod<T>(singleton, singletonClazz, "get")

        } catch (e: Exception) {
            Logger.w(TAG, "Failed to get singleton instance", e)
            null
        }
    }

    /**
     * 替换 Singleton 实例
     */
    fun replaceSingletonInstance(
        singletonClass: Class<*>,
        singletonFieldName: String,
        newInstance: Any,
        instanceFieldName: String = "mInstance"
    ): Boolean {
        return try {
            // 1. 获取 Singleton 对象
            val singleton = getStaticFieldValue<Any>(singletonClass, singletonFieldName)
                ?: return false

            // 2. 获取 Singleton 的 Class
            val singletonClazz = Class.forName("android.util.Singleton")

            // 3. 替换 mInstance
            setFieldValue(singleton, singletonClazz, instanceFieldName, newInstance)

        } catch (e: Exception) {
            Logger.w(TAG, "Failed to replace singleton instance", e)
            false
        }
    }

    /**
     * 从 ClassLoader 及其父类链中查找指定字段
     *
     * 用于 DEX 加载和 Native Library 加载场景，
     * 因为不同 Android 版本的 ClassLoader 实现可能将字段定义在不同层级
     *
     * @param classLoader 目标 ClassLoader
     * @param fieldName 字段名
     * @return Field 对象
     * @throws NoSuchFieldException 如果在整个继承链中都找不到该字段
     */
    @Throws(NoSuchFieldException::class)
    fun getClassLoaderField(classLoader: ClassLoader, fieldName: String): Field {
        var clazz: Class<*>? = classLoader.javaClass
        while (clazz != null) {
            try {
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                return field
            } catch (e: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        throw NoSuchFieldException("Field '$fieldName' not found in ClassLoader hierarchy")
    }
}
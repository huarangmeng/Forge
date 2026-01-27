package com.hrm.forge.internal.util

import java.lang.reflect.Field

/**
 * 反射工具类
 * 提供通用的反射操作方法
 */
internal object ReflectionUtils {
    
    /**
     * 从 ClassLoader 及其父类中查找指定的 Field
     * 
     * @param classLoader 目标 ClassLoader
     * @param fieldName 字段名
     * @return Field 对象
     * @throws NoSuchFieldException 如果字段不存在
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
    
    /**
     * 安全地获取 Field 的值
     * 
     * @param obj 对象实例
     * @param fieldName 字段名
     * @return 字段值，如果失败返回 null
     */
    fun getFieldValue(obj: Any, fieldName: String): Any? {
        return try {
            val field = obj.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.get(obj)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 安全地设置 Field 的值
     * 
     * @param obj 对象实例
     * @param fieldName 字段名
     * @param value 新值
     * @return 是否成功
     */
    fun setFieldValue(obj: Any, fieldName: String, value: Any?): Boolean {
        return try {
            val field = obj.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(obj, value)
            true
        } catch (e: Exception) {
            false
        }
    }
}

package com.hrm.forge.internal.hook

import android.content.ContentResolver
import android.net.Uri
import com.hrm.forge.internal.log.Logger
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * ContentResolver Hook 工具类
 *
 * 目的：解决热更 ContentProvider 调用 notifyChange() 时的跨进程通知问题
 *
 * 策略：Hook ContentResolver 内部的 IContentService Binder 代理
 * 通过动态代理拦截 notifyChange 方法，对热更 Provider 捕获 SecurityException
 */
internal object ContentResolverHook {

    private const val TAG = "ContentResolverHook"

    /**
     * Hook IContentService Binder 代理
     */
    fun hookIContentService(contentResolver: ContentResolver) {
        try {
            val contentResolverClass = ContentResolver::class.java

            // 尝试获取 IContentService 字段
            val contentServiceField = try {
                contentResolverClass.getDeclaredField("sContentService")
            } catch (e: NoSuchFieldException) {
                try {
                    contentResolverClass.getDeclaredField("mContentService")
                } catch (e2: NoSuchFieldException) {
                    Logger.w(TAG, "Cannot find IContentService field, trying instance field")
                    // 尝试从实例获取
                    contentResolver.javaClass.getDeclaredField("mContentService")
                }
            }

            contentServiceField.isAccessible = true

            var originalService = contentServiceField.get(contentResolver)

            // 如果 IContentService 为 null，主动触发初始化
            if (originalService == null) {
                Logger.d(TAG, "IContentService is null, triggering initialization...")

                // 调用 getContentService() 方法来初始化
                try {
                    val getContentServiceMethod =
                        contentResolverClass.getDeclaredMethod("getContentService")
                    getContentServiceMethod.isAccessible = true
                    originalService = getContentServiceMethod.invoke(contentResolver)

                    if (originalService == null) {
                        Logger.w(TAG, "Failed to initialize IContentService")
                        return
                    }

                    Logger.d(TAG, "IContentService initialized: ${originalService.javaClass.name}")

                } catch (e: Exception) {
                    Logger.w(TAG, "Cannot initialize IContentService", e)
                    return
                }
            } else {
                Logger.d(TAG, "Original IContentService: ${originalService.javaClass.name}")
            }

            // 获取 IContentService 接口
            val iContentServiceClass = Class.forName("android.content.IContentService")

            // 创建动态代理
            val proxyService = Proxy.newProxyInstance(
                iContentServiceClass.classLoader,
                arrayOf(iContentServiceClass),
                IContentServiceInvocationHandler(originalService)
            )

            // 替换 IContentService
            contentServiceField.set(contentResolver, proxyService)

            Logger.i(TAG, "✅ IContentService replaced with proxy")

        } catch (e: Exception) {
            Logger.e(TAG, "Failed to hook IContentService", e)
        }
    }

    /**
     * IContentService 动态代理处理器
     */
    private class IContentServiceInvocationHandler(
        private val originalService: Any
    ) : InvocationHandler {

        override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
            // 拦截 notifyChange 方法
            if (method.name == "notifyChange") {
                return handleNotifyChange(method, args)
            }

            // 其他方法直接转发
            return try {
                if (args != null) {
                    method.invoke(originalService, *args)
                } else {
                    method.invoke(originalService)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to invoke ${method.name}", e)
                throw e
            }
        }

        /**
         * 处理 notifyChange 调用
         *
         * IContentService.notifyChange 方法签名（不同版本可能不同）：
         * - void notifyChange(Uri uri, IContentObserver observer, boolean observerWantsSelfNotifications, int flags, int userHandle, int targetSdkVersion, String callingPackage)
         * - void notifyChange(Uri[] uris, IContentObserver observer, boolean observerWantsSelfNotifications, int flags, int userHandle, int targetSdkVersion, String callingPackage)
         */
        private fun handleNotifyChange(method: Method, args: Array<out Any>?): Any? {
            if (args == null || args.isEmpty()) {
                Logger.d(TAG, "notifyChange called with no args")
                return null
            }

            try {
                // 提取 URI（第一个参数可能是 Uri 或 Uri[]）
                val uris = when (val firstArg = args[0]) {
                    is Uri -> arrayOf(firstArg)
                    is Array<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        firstArg as? Array<Uri> ?: arrayOf()
                    }

                    else -> {
                        Logger.d(TAG, "Unknown URI type: ${firstArg?.javaClass?.name}")
                        // 类型未知，直接转发
                        return method.invoke(originalService, *args)
                    }
                }

                // 检查是否有热更 Provider 的 URI
                val hasHotUpdateProvider = uris.any { uri ->
                    val authority = uri?.authority
                    authority != null && ComponentManager.isProviderInHotUpdate(authority)
                }

                if (hasHotUpdateProvider) {
                    Logger.d(TAG, "🔔 Intercepted notifyChange for hot update provider")
                    Logger.d(TAG, "   URIs: ${uris.joinToString { it?.toString() ?: "null" }}")
                    Logger.d(TAG, "   Skip to avoid SecurityException")
                    // 直接返回，不调用系统方法
                    return null
                }

                // 非热更 Provider，正常调用
                return method.invoke(originalService, *args)

            } catch (e: SecurityException) {
                // 即使判断失误，也捕获异常
                Logger.w(TAG, "⚠️ Caught SecurityException in notifyChange")
                Logger.w(TAG, "   Message: ${e.message}")
                return null
            } catch (e: Exception) {
                Logger.e(TAG, "❌ Error in notifyChange handler", e)
                // 对于其他异常，记录但不抛出，避免影响业务
                return null
            }
        }
    }
}

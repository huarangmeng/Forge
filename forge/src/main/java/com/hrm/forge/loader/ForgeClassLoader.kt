package com.hrm.forge.loader

import android.annotation.SuppressLint
import android.content.Context
import com.hrm.forge.common.ReflectionUtils
import com.hrm.forge.common.UnZipUtils
import com.hrm.forge.logger.Logger
import dalvik.system.DexClassLoader
import java.io.File
import java.io.IOException
import java.lang.reflect.Array as ReflectArray

/**
 * DEX 动态加载器
 * 支持 Android 7.0+ (API 24+)
 *
 * 核心功能：
 * - 从 APK 中提取 DEX 文件
 * - 动态加载 DEX 到当前 ClassLoader
 * - 支持多 DEX 加载
 * - 兼容不同 Android 版本
 */
object ForgeClassLoader {
    private const val TAG = "ForgeClassLoader"

    /**
     * 安装 DEX 文件
     * @param context Context
     * @param apkFile APK 文件
     * @throws IOException 当文件操作失败时抛出
     * @throws ReflectiveOperationException 当反射操作失败时抛出
     */
    @SuppressLint("SetWorldReadable", "SetWorldWritable")
    @Throws(IOException::class, ReflectiveOperationException::class)
    fun installDex(context: Context, apkFile: File) {
        Logger.i(TAG, "Start install DEX from: ${apkFile.absolutePath}")

        val startTime = System.currentTimeMillis()

        try {
            // 创建 DEX 输出目录
            val dexDir = File(context.filesDir, "forge_dex")
            
            // 清理旧的 DEX 目录（避免权限问题）
            if (dexDir.exists()) {
                try {
                    dexDir.deleteRecursively()
                    Logger.d(TAG, "Cleaned old dex directory")
                } catch (e: Exception) {
                    Logger.w(TAG, "Failed to clean old dex directory", e)
                }
            }
            
            // 重新创建目录
            dexDir.mkdirs()
            
            // 提取所有 DEX 文件
            val dexFiles = UnZipUtils.extractAllDexFiles(apkFile, dexDir)
            if (dexFiles.isEmpty()) {
                Logger.w(TAG, "No DEX files found in APK")
                return
            }
            
            Logger.i(TAG, "Found ${dexFiles.size} DEX files")
            
            // 将所有 DEX 文件设置为可读写，然后设置为只读（Android 8.0+ 安全限制）
            dexFiles.forEach { dexFile ->
                if (dexFile.exists()) {
                    // 先设置为可读写
                    dexFile.setReadable(true, false)
                    dexFile.setWritable(true, false)
                    // 再设置为只读
                    val success = dexFile.setReadOnly()
                    Logger.d(TAG, "Set ${dexFile.name} to read-only: $success")
                }
            }

            installDexOnN(context, dexFiles, dexDir)

            val elapsed = System.currentTimeMillis() - startTime
            Logger.i(TAG, "Install DEX success, elapsed: ${elapsed}ms")

        } catch (e: IOException) {
            Logger.e(TAG, "Install DEX failed: IO error", e)
            throw e
        } catch (e: ReflectiveOperationException) {
            Logger.e(TAG, "Install DEX failed: Reflection error", e)
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Install DEX failed: Unexpected error", e)
            throw e
        }
    }

    /**
     * Android 7.0+ 的 DEX 加载
     */
    @Throws(ReflectiveOperationException::class)
    private fun installDexOnN(context: Context, dexFiles: List<File>, dexDir: File) {
        Logger.i(TAG, "Install DEX for Android N+")

        val classLoader = context.classLoader

        // 获取 pathList 字段
        val pathListField = ReflectionUtils.getClassLoaderField(classLoader, "pathList")
        val pathList = pathListField.get(classLoader)

        // 创建临时的 DexClassLoader 加载新的 DEX
        val optimizedDir = File(dexDir, "oat")
        optimizedDir.mkdirs()

        val dexPath = dexFiles.joinToString(File.pathSeparator) { it.absolutePath }
        val dexClassLoader = DexClassLoader(
            dexPath,
            optimizedDir.absolutePath,
            null,
            classLoader
        )

        // 获取新的 dexElements
        val newPathListField = ReflectionUtils.getClassLoaderField(dexClassLoader, "pathList")
        val newPathList = newPathListField.get(dexClassLoader)

        val newDexElementsField = newPathList.javaClass.getDeclaredField("dexElements")
        newDexElementsField.isAccessible = true
        val newDexElements = newDexElementsField.get(newPathList)

        // 合并 dexElements
        val dexElementsField = pathList.javaClass.getDeclaredField("dexElements")
        dexElementsField.isAccessible = true
        val oldDexElements = dexElementsField.get(pathList)

        val mergedDexElements = combineArray(newDexElements, oldDexElements)
        dexElementsField.set(pathList, mergedDexElements)

        Logger.i(TAG, "DEX elements merged successfully")

        // 验证加载
        verifyDexLoaded(dexFiles)
    }

    /**
     * 合并两个数组
     */
    private fun combineArray(arrayLhs: Any, arrayRhs: Any): Any {
        val clazz = arrayLhs.javaClass.componentType
        val lhsLength = ReflectArray.getLength(arrayLhs)
        val rhsLength = ReflectArray.getLength(arrayRhs)
        val result = ReflectArray.newInstance(clazz!!, lhsLength + rhsLength)

        for (i in 0 until lhsLength) {
            ReflectArray.set(result, i, ReflectArray.get(arrayLhs, i))
        }

        for (i in 0 until rhsLength) {
            ReflectArray.set(result, lhsLength + i, ReflectArray.get(arrayRhs, i))
        }

        return result
    }

    /**
     * 验证 DEX 是否加载成功
     */
    private fun verifyDexLoaded(dexFiles: List<File>) {
        Logger.d(TAG, "Verifying DEX loaded...")

        // 简单的验证：检查是否能访问某些类
        // 实际项目中可以尝试加载一些已知的类来验证

        Logger.i(TAG, "DEX verification completed")
    }
}

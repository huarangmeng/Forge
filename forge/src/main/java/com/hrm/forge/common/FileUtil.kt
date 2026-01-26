package com.hrm.forge.common

import com.hrm.forge.logger.Logger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * 文件工具类
 */
object FileUtil {
    private const val TAG = "FileUtil"
    
    /**
     * 计算文件 SHA1
     */
    fun getFileSHA1(file: File): String? {
        if (!file.exists() || !file.isFile) {
            return null
        }
        
        return try {
            val digest = MessageDigest.getInstance("SHA-1")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var len: Int
                while (fis.read(buffer).also { len = it } != -1) {
                    digest.update(buffer, 0, len)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Logger.e(TAG, "Calculate SHA1 failed: ${file.absolutePath}", e)
            null
        }
    }
    
    /**
     * 复制文件
     */
    fun copyFile(src: File, dest: File): Boolean {
        if (!src.exists() || !src.isFile) {
            Logger.e(TAG, "Source file not exists: ${src.absolutePath}")
            return false
        }
        
        return try {
            dest.parentFile?.mkdirs()
            FileInputStream(src).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Copy file failed: ${src.absolutePath} -> ${dest.absolutePath}", e)
            false
        }
    }
    
    /**
     * 删除文件或目录
     */
    fun deleteRecursively(file: File): Boolean {
        return try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { deleteRecursively(it) }
            }
            file.delete()
        } catch (e: Exception) {
            Logger.e(TAG, "Delete failed: ${file.absolutePath}", e)
            false
        }
    }
    
    /**
     * 确保目录存在
     */
    fun ensureDir(dir: File): Boolean {
        return if (dir.exists()) {
            dir.isDirectory
        } else {
            dir.mkdirs()
        }
    }
    
    /**
     * 从 InputStream 写入文件
     */
    fun writeStreamToFile(input: InputStream, dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Write stream to file failed: ${dest.absolutePath}", e)
            false
        }
    }
    
    /**
     * 获取文件大小（字节）
     */
    fun getFileSize(file: File): Long {
        return if (file.exists() && file.isFile) {
            file.length()
        } else {
            0L
        }
    }
    
    /**
     * 获取目录大小（递归）
     */
    fun getDirSize(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) {
            return 0L
        }
        
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                getDirSize(file)
            } else {
                file.length()
            }
        }
        return size
    }
    
    /**
     * 检查文件是否有效
     */
    fun isValidFile(file: File): Boolean {
        return file.exists() && file.isFile && file.length() > 0
    }
}

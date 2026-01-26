package com.hrm.forge.common

import com.hrm.forge.logger.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipFile

/**
 * APK 解压工具类
 *
 * 提供安全的 ZIP 文件解压功能，防止路径穿越攻击
 */
object UnZipUtils {
    private const val TAG = "UnZipUtils"

    /**
     * 验证解压路径是否安全（防止路径穿越攻击）
     *
     * @param destDir 目标目录
     * @param entryFile 条目文件
     * @throws SecurityException 如果路径不安全
     */
    @Throws(SecurityException::class)
    private fun validateExtractPath(destDir: File, entryFile: File) {
        val destPath = destDir.canonicalPath
        val filePath = entryFile.canonicalPath

        if (!filePath.startsWith(destPath + File.separator) && filePath != destPath) {
            throw SecurityException(
                "Entry is outside of the target directory! " +
                        "Dest: $destPath, File: $filePath"
            )
        }
    }

    /**
     * 解压 APK 文件
     * @param apkFile APK 文件
     * @param destDir 目标目录
     * @return 是否成功
     */
    fun unzipApk(apkFile: File, destDir: File): Boolean {
        if (!apkFile.exists() || !apkFile.isFile) {
            Logger.e(TAG, "APK file not exists: ${apkFile.absolutePath}")
            return false
        }

        return try {
            if (!FileUtil.ensureDir(destDir)) {
                Logger.e(TAG, "Cannot create dest dir: ${destDir.absolutePath}")
                return false
            }

            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val entryFile = File(destDir, entry.name)

                    // 安全检查：防止路径穿越攻击
                    validateExtractPath(destDir, entryFile)

                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(entryFile).use { output ->
                                val buffer = ByteArray(Constants.FILE_BUFFER_SIZE)
                                var count: Int
                                while (input.read(buffer).also { count = it } != -1) {
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                    }
                }
            }

            Logger.i(TAG, "Unzip APK success: ${apkFile.absolutePath}")
            true
        } catch (e: SecurityException) {
            Logger.e(TAG, "Security violation while unzipping: ${apkFile.absolutePath}", e)
            false
        } catch (e: IOException) {
            Logger.e(TAG, "IO error while unzipping: ${apkFile.absolutePath}", e)
            false
        } catch (e: Exception) {
            Logger.e(TAG, "Unexpected error while unzipping: ${apkFile.absolutePath}", e)
            false
        }
    }

    /**
     * 解压指定文件
     * @param apkFile APK 文件
     * @param entryName 条目名称（如 "classes.dex"）
     * @param destFile 目标文件
     * @return 是否成功
     */
    fun extractFile(apkFile: File, entryName: String, destFile: File): Boolean {
        if (!apkFile.exists() || !apkFile.isFile) {
            Logger.e(TAG, "APK file not exists: ${apkFile.absolutePath}")
            return false
        }

        return try {
            val parentDir = destFile.parentFile ?: return false

            // 安全检查
            validateExtractPath(parentDir, destFile)

            parentDir.mkdirs()

            ZipFile(apkFile).use { zip ->
                val entry = zip.getEntry(entryName)
                if (entry == null) {
                    Logger.e(TAG, "Entry not found: $entryName")
                    return false
                }

                zip.getInputStream(entry).use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(Constants.FILE_BUFFER_SIZE)
                        var count: Int
                        while (input.read(buffer).also { count = it } != -1) {
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }

            Logger.i(TAG, "Extract file success: $entryName -> ${destFile.absolutePath}")
            true
        } catch (e: SecurityException) {
            Logger.e(TAG, "Security violation while extracting: $entryName", e)
            false
        } catch (e: IOException) {
            Logger.e(TAG, "IO error while extracting: $entryName", e)
            false
        } catch (e: Exception) {
            Logger.e(TAG, "Unexpected error while extracting: $entryName", e)
            false
        }
    }

    /**
     * 获取 APK 中的所有 dex 文件
     */
    fun extractAllDexFiles(apkFile: File, destDir: File): List<File> {
        val dexFiles = mutableListOf<File>()
        
        if (!apkFile.exists() || !apkFile.isFile) {
            Logger.e(TAG, "APK file not exists: ${apkFile.absolutePath}")
            return dexFiles
        }
        
        try {
            if (!FileUtil.ensureDir(destDir)) {
                Logger.e(TAG, "Cannot create dest dir: ${destDir.absolutePath}")
                return dexFiles
            }
            
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    
                    if (name.endsWith(".dex") && !entry.isDirectory) {
                        val dexFile = File(destDir, name)
                        
                        // 安全检查
                        validateExtractPath(destDir, dexFile)
                        
                        // 确保父目录存在
                        dexFile.parentFile?.mkdirs()
                        
                        // 如果文件已存在，先删除（避免权限问题）
                        if (dexFile.exists()) {
                            try {
                                dexFile.delete()
                                Logger.d(TAG, "Deleted existing dex file: $name")
                            } catch (e: Exception) {
                                Logger.w(TAG, "Failed to delete existing dex file: $name", e)
                                // 尝试强制删除
                                dexFile.setWritable(true)
                                dexFile.delete()
                            }
                        }
                        
                        // 写入新文件
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(dexFile).use { output ->
                                val buffer = ByteArray(Constants.FILE_BUFFER_SIZE)
                                var count: Int
                                while (input.read(buffer).also { count = it } != -1) {
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        
                        // 设置文件权限为可读
                        dexFile.setReadable(true, false)
                        
                        dexFiles.add(dexFile)
                        Logger.d(TAG, "Extracted dex: $name")
                    }
                }
            }
            
            Logger.i(TAG, "Extracted ${dexFiles.size} dex files")
        } catch (e: SecurityException) {
            Logger.e(TAG, "Security violation while extracting dex files", e)
        } catch (e: IOException) {
            Logger.e(TAG, "IO error while extracting dex files", e)
        } catch (e: Exception) {
            Logger.e(TAG, "Unexpected error while extracting dex files", e)
        }

        return dexFiles
    }
}

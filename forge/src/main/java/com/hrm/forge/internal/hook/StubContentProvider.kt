package com.hrm.forge.internal.hook

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.hrm.forge.internal.log.Logger

/**
 * 占坑 ContentProvider
 * 
 * 作用：
 * 1. 在主 APK Manifest 中注册，占据一个 Authority 槽位
 * 2. 确保主 APK 有至少一个 ContentProvider（某些系统可能需要）
 * 
 * 注意：
 * - 这个 Provider 不应该被直接调用
 * - 热更 Provider 通过动态注册机制独立工作
 */
internal class StubContentProvider : ContentProvider() {
    
    companion object {
        private const val TAG = "StubContentProvider"
        const val STUB_AUTHORITY = "com.hrm.forge.stub.provider"
    }

    override fun onCreate(): Boolean {
        Logger.i(TAG, "StubContentProvider onCreate()")
        Logger.i(TAG, "Authority: $STUB_AUTHORITY")
        Logger.i(TAG, "This is a placeholder provider for hot update framework")
        return true
    }
    
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        Logger.w(TAG, "StubContentProvider.query() called directly")
        Logger.w(TAG, "URI: $uri")
        return null
    }
    
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        Logger.w(TAG, "StubContentProvider.insert() called directly")
        Logger.w(TAG, "URI: $uri")
        return null
    }
    
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        Logger.w(TAG, "StubContentProvider.update() called directly")
        Logger.w(TAG, "URI: $uri")
        return 0
    }
    
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        Logger.w(TAG, "StubContentProvider.delete() called directly")
        Logger.w(TAG, "URI: $uri")
        return 0
    }
    
    override fun getType(uri: Uri): String? {
        Logger.w(TAG, "StubContentProvider.getType() called")
        Logger.w(TAG, "URI: $uri")
        return null
    }
}

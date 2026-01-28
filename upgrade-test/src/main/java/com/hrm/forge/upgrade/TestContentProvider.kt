package com.hrm.forge.upgrade

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log

/**
 * 测试 ContentProvider
 * 
 * 这个 ContentProvider 不会在主 APK 的 AndroidManifest 中注册
 * 用于测试热更新框架的 ContentProvider Hook 功能
 * 
 * Authority: com.hrm.forge.upgrade.test.provider
 * 
 * 支持的 URI：
 * - content://com.hrm.forge.upgrade.test.provider/users
 * - content://com.hrm.forge.upgrade.test.provider/users/{id}
 * 
 * 测试方法：
 * 1. 构建并部署 upgrade-test APK 到热更新目录
 * 2. 使用 HotUpdateTester.testQueryProvider() 查询数据
 * 3. 使用 HotUpdateTester.testInsertProvider() 插入数据
 * 4. 检查日志，验证 ContentProvider 是否正常工作
 */
class TestContentProvider : ContentProvider() {
    
    companion object {
        private const val TAG = "TestContentProvider"
        
        // Authority
        const val AUTHORITY = "com.hrm.forge.upgrade.test.provider"
        
        // URI 匹配码
        private const val USERS = 1
        private const val USER_ID = 2
        
        // URI Matcher
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "users", USERS)
            addURI(AUTHORITY, "users/#", USER_ID)
        }
    }
    
    // 模拟数据存储（实际应用中应使用数据库）
    private val dataStore = mutableListOf<User>()
    
    data class User(
        val id: Long,
        val name: String,
        val age: Int,
        val timestamp: Long
    )
    
    override fun onCreate(): Boolean {
        Log.i(TAG, "🎉 TestContentProvider onCreate() - Provider 创建成功！")
        Log.i(TAG, "Authority: $AUTHORITY")
        Log.i(TAG, "这是来自热更新 APK 的 ContentProvider，未在主 APK AndroidManifest 中注册")
        
        // 初始化一些测试数据
        dataStore.add(User(1, "张三", 25, System.currentTimeMillis()))
        dataStore.add(User(2, "李四", 30, System.currentTimeMillis()))
        dataStore.add(User(3, "王五", 28, System.currentTimeMillis()))
        
        Log.i(TAG, "✅ 初始化了 ${dataStore.size} 条测试数据")
        return true
    }
    
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        Log.i(TAG, "📖 query() 被调用")
        Log.i(TAG, "URI: $uri")
        
        when (uriMatcher.match(uri)) {
            USERS -> {
                Log.i(TAG, "查询所有用户，当前共 ${dataStore.size} 条数据")
                return createCursor(dataStore)
            }
            USER_ID -> {
                val id = uri.lastPathSegment?.toLongOrNull()
                Log.i(TAG, "查询指定用户 ID: $id")
                val user = dataStore.find { it.id == id }
                return if (user != null) {
                    createCursor(listOf(user))
                } else {
                    Log.w(TAG, "未找到 ID 为 $id 的用户")
                    createCursor(emptyList())
                }
            }
            else -> {
                Log.e(TAG, "不支持的 URI: $uri")
                throw IllegalArgumentException("Unsupported URI: $uri")
            }
        }
    }
    
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        Log.i(TAG, "➕ insert() 被调用")
        Log.i(TAG, "URI: $uri")
        
        if (values == null) {
            Log.e(TAG, "ContentValues 为空")
            return null
        }
        
        when (uriMatcher.match(uri)) {
            USERS -> {
                val name = values.getAsString("name") ?: "Unknown"
                val age = values.getAsInteger("age") ?: 0
                val timestamp = values.getAsLong("timestamp") ?: System.currentTimeMillis()
                
                val newId = (dataStore.maxOfOrNull { it.id } ?: 0) + 1
                val user = User(newId, name, age, timestamp)
                dataStore.add(user)
                
                Log.i(TAG, "✅ 成功插入用户: $user")
                Log.i(TAG, "当前共 ${dataStore.size} 条数据")
                
                val resultUri = Uri.parse("content://$AUTHORITY/users/$newId")
                context?.contentResolver?.notifyChange(resultUri, null)
                return resultUri
            }
            else -> {
                Log.e(TAG, "不支持的 URI: $uri")
                throw IllegalArgumentException("Unsupported URI: $uri")
            }
        }
    }
    
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        Log.i(TAG, "🔄 update() 被调用")
        Log.i(TAG, "URI: $uri")
        
        if (values == null) {
            Log.e(TAG, "ContentValues 为空")
            return 0
        }
        
        when (uriMatcher.match(uri)) {
            USER_ID -> {
                val id = uri.lastPathSegment?.toLongOrNull()
                val index = dataStore.indexOfFirst { it.id == id }
                
                if (index != -1) {
                    val oldUser = dataStore[index]
                    val name = values.getAsString("name") ?: oldUser.name
                    val age = values.getAsInteger("age") ?: oldUser.age
                    
                    val updatedUser = oldUser.copy(
                        name = name,
                        age = age,
                        timestamp = System.currentTimeMillis()
                    )
                    dataStore[index] = updatedUser
                    
                    Log.i(TAG, "✅ 成功更新用户: $oldUser -> $updatedUser")
                    context?.contentResolver?.notifyChange(uri, null)
                    return 1
                } else {
                    Log.w(TAG, "未找到 ID 为 $id 的用户")
                    return 0
                }
            }
            else -> {
                Log.e(TAG, "不支持的 URI: $uri")
                throw IllegalArgumentException("Unsupported URI: $uri")
            }
        }
    }
    
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        Log.i(TAG, "🗑️ delete() 被调用")
        Log.i(TAG, "URI: $uri")
        
        when (uriMatcher.match(uri)) {
            USER_ID -> {
                val id = uri.lastPathSegment?.toLongOrNull()
                val removed = dataStore.removeIf { it.id == id }
                
                if (removed) {
                    Log.i(TAG, "✅ 成功删除用户 ID: $id")
                    Log.i(TAG, "当前共 ${dataStore.size} 条数据")
                    context?.contentResolver?.notifyChange(uri, null)
                    return 1
                } else {
                    Log.w(TAG, "未找到 ID 为 $id 的用户")
                    return 0
                }
            }
            USERS -> {
                val count = dataStore.size
                dataStore.clear()
                Log.i(TAG, "✅ 已清空所有数据，删除了 $count 条记录")
                context?.contentResolver?.notifyChange(uri, null)
                return count
            }
            else -> {
                Log.e(TAG, "不支持的 URI: $uri")
                throw IllegalArgumentException("Unsupported URI: $uri")
            }
        }
    }
    
    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            USERS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.users"
            USER_ID -> "vnd.android.cursor.item/vnd.$AUTHORITY.users"
            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
    }
    
    /**
     * 创建 Cursor 用于返回查询结果
     */
    private fun createCursor(users: List<User>): Cursor {
        val cursor = MatrixCursor(arrayOf("id", "name", "age", "timestamp"))
        
        users.forEach { user ->
            cursor.addRow(arrayOf(user.id, user.name, user.age, user.timestamp))
        }
        
        return cursor
    }
}

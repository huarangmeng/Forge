package com.hrm.forge.internal.hook

import android.app.Activity
import android.os.Bundle

/**
 * 占坑 Activity - Standard 模式
 * launchMode = "standard"
 */
internal class StubActivityStandard : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}

/**
 * 占坑 Activity - SingleTop 模式
 * launchMode = "singleTop"
 */
internal class StubActivitySingleTop : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}

/**
 * 占坑 Activity - SingleTask 模式
 * launchMode = "singleTask"
 */
internal class StubActivitySingleTask : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}

/**
 * 占坑 Activity - SingleInstance 模式
 * launchMode = "singleInstance"
 */
internal class StubActivitySingleInstance : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}

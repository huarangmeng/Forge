package com.hrm.forge.demo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrm.forge.demo.theme.ForgeTheme

class MainActivity : ComponentActivity() {

    private lateinit var hotUpdateManager: HotUpdateManager

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hotUpdateManager = HotUpdateManager(this)

        enableEdgeToEdge()
        setContent {
            ForgeTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(), topBar = {
                        TopAppBar(
                            title = { Text("Forge Demo") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        hotUpdateManager = hotUpdateManager,
                        onShowToast = { message ->
                            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        })
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier, hotUpdateManager: HotUpdateManager, onShowToast: (String) -> Unit
) {
    var versionInfo by remember { mutableStateOf(hotUpdateManager.getVersionInfo()) }
    var isProcessing by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 版本信息卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (versionInfo.isHotUpdateLoaded) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "版本信息",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 基础版本信息
                Text(
                    text = "基础版本",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                InfoRow("版本名称", versionInfo.baseVersion)
                InfoRow("版本号", versionInfo.baseVersionCode.toString())

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 当前运行版本
                Text(
                    text = "当前运行版本",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                InfoRow("版本名称", versionInfo.currentVersion)
                InfoRow("版本号", versionInfo.currentVersionCode.toString())

                if (versionInfo.isHotUpdateLoaded) {
                    Text(
                        text = "✓ 已加载热更新",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    if (versionInfo.buildNumber != null) {
                        InfoRow("构建号", versionInfo.buildNumber.toString())
                    }

                    if (versionInfo.apkPath != null) {
                        InfoRow("APK 路径", versionInfo.apkPath!!, isPath = true)
                    }

                    if (versionInfo.sha1 != null) {
                        InfoRow("SHA1", versionInfo.sha1!!, isPath = true)
                    }
                } else {
                    Text(
                        text = "未加载热更新",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // 下次启动版本（如果与当前不同）
                if (versionInfo.hasPendingChange) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    Text(
                        text = "下次启动版本（待生效）",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    InfoRow("版本名称", versionInfo.nextVersion)
                    InfoRow("版本号", versionInfo.nextVersionCode.toString())
                    
                    Text(
                        text = "⚠️ 需要重启应用才能生效",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // 操作按钮
        Text(
            text = "热更新操作",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        // 刷新版本信息
        Button(
            onClick = {
                versionInfo = hotUpdateManager.getVersionInfo()
                onShowToast("版本信息已刷新")
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isProcessing
        ) {
            Text("刷新版本信息")
        }

        // 从 Assets 加载热更新
        Button(
            onClick = {
                isProcessing = true
                hotUpdateManager.releaseFromAssets(
                    assetFileName = "app-debug.apk"
                ) { result, message ->
                    isProcessing = false
                    onShowToast(message)
                    if (result.isSuccess) {
                        versionInfo = hotUpdateManager.getVersionInfo()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isProcessing
        ) {
            Text("从 Assets 加载热更新")
        }

        // 回滚到上一版本
        OutlinedButton(
            onClick = {
                isProcessing = true
                hotUpdateManager.rollbackToLastVersion { success, message ->
                    isProcessing = false
                    onShowToast(message)
                    if (success) {
                        versionInfo = hotUpdateManager.getVersionInfo()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isProcessing && versionInfo.canRollback
        ) {
            Text("回滚到上一版本")
        }

        // 清理上一版本
        OutlinedButton(
            onClick = {
                isProcessing = true
                hotUpdateManager.cleanLastVersion { success, message ->
                    isProcessing = false
                    onShowToast(message)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isProcessing
        ) {
            Text("清理上一版本")
        }

        // 测试热更新 Activity
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🧪 Activity 测试",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                
                Text(
                    text = "测试启动热更新 APK 中新增的 Activity",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
                
                // 测试 upgrade-test 模块中的 Activity
                Button(
                    onClick = {
                        hotUpdateManager.testLaunchActivity(
                            "com.hrm.forge.upgrade.UpgradeActivity"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Text("启动 UpgradeActivity")
                }
            }
        }

        // 测试热更新 Service
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🔧 Service 测试",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                
                Text(
                    text = "测试启动热更新 APK 中新增的 Service（查看 Logcat 日志验证）",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
                
                // 测试 TestService (startService)
                Button(
                    onClick = {
                        hotUpdateManager.testStartService(
                            "com.hrm.forge.upgrade.TestService"
                        )
                        onShowToast("已启动 TestService，请查看 Logcat")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Text("启动 TestService")
                }
                
                // 停止 TestService
                OutlinedButton(
                    onClick = {
                        hotUpdateManager.testStopService(
                            "com.hrm.forge.upgrade.TestService"
                        )
                        onShowToast("已停止 TestService")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Text("停止 TestService")
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                // 测试 TestBindService (bindService)
                Button(
                    onClick = {
                        hotUpdateManager.testStartService(
                            "com.hrm.forge.upgrade.TestBindService"
                        )
                        onShowToast("已启动 TestBindService，请查看 Logcat")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Text("启动 TestBindService")
                }
                
                // 停止 TestBindService
                OutlinedButton(
                    onClick = {
                        hotUpdateManager.testStopService(
                            "com.hrm.forge.upgrade.TestBindService"
                        )
                        onShowToast("已停止 TestBindService")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Text("停止 TestBindService")
                }
                
                Text(
                    text = "💡 提示：查看 Logcat 过滤 'TestService' 或 'StubService' 标签",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // BroadcastReceiver 测试卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📡 BroadcastReceiver 测试",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                
                Text(
                    text = "测试热更新 APK 中新增的 BroadcastReceiver（查看 Logcat 日志验证）",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
                
                Text(
                    text = "1. 动态注册测试",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                
                // 动态注册 DynamicTestReceiver
                Button(
                    onClick = {
                        hotUpdateManager.testRegisterReceiver(
                            "com.hrm.forge.upgrade.DynamicTestReceiver",
                            "com.hrm.forge.DYNAMIC_ACTION"
                        )
                        onShowToast("已动态注册 DynamicTestReceiver")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Text("动态注册 Receiver")
                }
                
                // 发送隐式广播（测试动态注册）
                Button(
                    onClick = {
                        hotUpdateManager.testSendImplicitBroadcast(
                            "com.hrm.forge.DYNAMIC_ACTION"
                        )
                        onShowToast("已发送隐式广播，请查看 Logcat")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Text("发送广播到动态 Receiver")
                }
                
                // 取消注册 DynamicTestReceiver
                OutlinedButton(
                    onClick = {
                        hotUpdateManager.testUnregisterReceiver(
                            "com.hrm.forge.upgrade.DynamicTestReceiver"
                        )
                        onShowToast("已取消注册 DynamicTestReceiver")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Text("取消注册 Receiver")
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                Text(
                    text = "2. 静态注册测试（应用运行时）",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                
                // 发送自定义隐式广播（测试静态注册）
                Button(
                    onClick = {
                        hotUpdateManager.testSendCustomImplicitBroadcast()
                        onShowToast("已发送隐式广播，请查看 Logcat")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Text("发送广播到静态 Receiver")
                }
                
                Text(
                    text = "💡 ImplicitTestReceiver 在热更新 APK 的 Manifest 中静态注册，Forge 自动解析 IntentFilter 并拦截匹配的隐式广播",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                Text(
                    text = "⚠️ 限制：应用未运行时无法接收广播（需要进程存活）",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp)
                )
                
                Text(
                    text = "🔍 查看 Logcat 过滤 'ImplicitTestReceiver' 或 'ComponentManager' 标签",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // ContentProvider 测试卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📦 ContentProvider 测试",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                Text(
                    text = "测试热更新 APK 中新增的 ContentProvider（查看 Logcat 日志验证）",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                
                // 查询所有用户
                Button(
                    onClick = {
                        hotUpdateManager.testQueryProvider(
                            authority = "com.hrm.forge.upgrade.test.provider",
                            path = "users"
                        )
                        onShowToast("查询操作已执行，请查看 Logcat")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Text("查询所有用户")
                }
                
                // 查询指定用户
                Button(
                    onClick = {
                        hotUpdateManager.testQueryProvider(
                            authority = "com.hrm.forge.upgrade.test.provider",
                            path = "users/1"
                        )
                        onShowToast("查询用户 ID=1，请查看 Logcat")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Text("查询用户 (ID=1)")
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                // 插入新用户
                Button(
                    onClick = {
                        hotUpdateManager.testInsertProvider(
                            authority = "com.hrm.forge.upgrade.test.provider",
                            path = "users"
                        )
                        onShowToast("插入操作已执行，请查看 Logcat")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Text("插入新用户")
                }
                
                Text(
                    text = "💡 TestContentProvider 在热更新 APK 的 Manifest 中声明，Forge 通过占坑 Provider 和 Hook 机制实现热更新",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                Text(
                    text = "🔍 Authority: com.hrm.forge.upgrade.test.provider",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
                
                Text(
                    text = "🔍 查看 Logcat 过滤 'TestContentProvider' 或 'ContentProviderHook' 标签",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // 说明文本
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "使用说明",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Text(
                    text = """
                        版本管理：
                        • 基础版本：应用 APK 的原始版本
                        • 当前版本：实际运行的版本（热更新或基础）
                        • 未加载热更新时，当前版本 = 基础版本
                        • 加载热更新后，当前版本 = 热更新版本
                        • 构建号、APK路径、SHA1 仅在热更新时显示
                        
                        操作说明：
                        • 点击"从 Assets 加载热更新"可加载测试 APK
                        • 发布成功后需要重启应用才能生效
                        • "回滚到上一版本"支持回滚到基础版本（清除热更新）
                        • 首次加载热更新后，可回滚到未加载状态
                        
                        热更新测试：
                        • Activity：启动未在主 APK 中注册的 Activity
                        • Service：启动未在主 APK 中注册的 Service
                        • BroadcastReceiver：
                          ✅ 动态注册：完全支持，与普通 Receiver 无区别
                          ✅ 静态注册：支持在应用运行时接收广播
                          ❌ 应用未运行时：无法接收广播（需要进程存活）
                        • ContentProvider：
                          ✅ 查询操作：支持 query() 方法
                          ✅ 插入操作：支持 insert() 方法
                          ✅ 更新/删除：支持 update()/delete() 方法
                          ✅ 使用真实 Authority 直接访问
                        • 通过 Logcat 查看测试日志验证功能
                    """.trimIndent(),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // 加载中提示
        if (isProcessing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, isPath: Boolean = false) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            maxLines = if (isPath) 2 else 1,
            fontWeight = FontWeight.Medium
        )
    }
}
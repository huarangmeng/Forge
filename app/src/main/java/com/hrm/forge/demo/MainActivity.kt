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
                    text = "当前版本信息",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                InfoRow("基础版本", versionInfo.baseVersion)
                InfoRow("基础版本号", versionInfo.baseVersionCode.toString())

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                InfoRow("当前版本", versionInfo.currentVersion)
                InfoRow("当前版本号", versionInfo.currentVersionCode.toString())

                if (versionInfo.isHotUpdateLoaded) {
                    Text(
                        text = "✓ 已加载热更新",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    if (versionInfo.buildNumber != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
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
                ) { success, message ->
                    isProcessing = false
                    onShowToast(message)
                    if (success) {
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
            enabled = !isProcessing && versionInfo.isHotUpdateLoaded
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
                    text = "🧪 热更新测试",
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
                        HotUpdateTester.testLaunchActivity(
                            hotUpdateManager.context,
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
                        • 基础版本：应用 APK 的原始版本
                        • 当前版本：实际运行的版本（热更新或基础）
                        • 未加载热更新时，当前版本 = 基础版本
                        • 加载热更新后，当前版本 = 热更新版本
                        • 构建号、APK路径、SHA1 仅在热更新时显示
                        • 点击"从 Assets 加载热更新"可加载测试 APK
                        • 发布成功后需要重启应用才能生效
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
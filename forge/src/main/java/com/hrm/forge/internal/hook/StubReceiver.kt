package com.hrm.forge.internal.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hrm.forge.internal.log.Logger

/**
 * 占坑 BroadcastReceiver
 * 
 * 与 Activity/Service 的占坑机制一致：
 * - Activity: 通过 Instrumentation Hook 拦截，替换为 StubActivity
 * - Service: 通过 AMS Hook 拦截，替换为 StubService
 * - BroadcastReceiver: 通过 AMS Hook 拦截 registerReceiver，替换为 StubReceiver
 * 
 * 工作流程：
 * 1. AMSHook 拦截 registerReceiver() 调用
 * 2. 检查 Receiver 是否在主 APK 中注册
 * 3. 如果未注册但在热更新 APK 中存在，将真实 Receiver 信息保存到 Intent
 * 4. StubReceiver 收到广播后，从 Intent 中恢复真实 Receiver 并调用
 * 
 * 注意：与 Activity/Service 一致，直接使用 Class.forName() 加载类
 *      因为 DexLoader 已经将热更新的 DEX 合并到主 ClassLoader 中
 */
class StubReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "StubReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Logger.e(TAG, "Context or Intent is null")
            return
        }

        // 从 Intent 中获取真实 Receiver 的类名
        val realReceiverName = intent.getStringExtra(ComponentManager.KEY_REAL_RECEIVER)
        if (realReceiverName.isNullOrEmpty()) {
            Logger.e(TAG, "Real receiver name is null or empty in intent")
            return
        }

        Logger.d(TAG, "Receive broadcast for: $realReceiverName")
        Logger.d(TAG, "Action: ${intent.action}")

        try {
            // 直接使用 Class.forName() 加载类（与 Activity/Service 一致）
            // DexLoader 已经将热更新的 DEX 合并到主 ClassLoader 中
            val realReceiverClass = Class.forName(realReceiverName)
            val realReceiver = realReceiverClass.newInstance() as BroadcastReceiver

            // 创建一个新的 Intent，移除占坑标记
            val cleanIntent = Intent(intent).apply {
                removeExtra(ComponentManager.KEY_REAL_RECEIVER)
            }

            // 调用真实 Receiver 的 onReceive
            Logger.d(TAG, "Dispatching to real receiver: $realReceiverName")
            
            // 如果是有序广播，需要先设置真实 Receiver 的初始结果状态
            if (isOrderedBroadcast) {
                // 将当前 StubReceiver 的结果状态传递给真实 Receiver
                realReceiver.setResultCode(resultCode)
                realReceiver.setResultData(resultData)
                realReceiver.setResultExtras(getResultExtras(true))
            }
            
            // 调用真实 Receiver
            realReceiver.onReceive(context, cleanIntent)

            // 如果是有序广播，将真实 Receiver 的结果传递回去
            if (isOrderedBroadcast) {
                setResult(
                    realReceiver.resultCode,
                    realReceiver.resultData,
                    realReceiver.getResultExtras(true)
                )
            }

            Logger.d(TAG, "✅ Successfully dispatched to $realReceiverName")
        } catch (e: ClassNotFoundException) {
            Logger.e(TAG, "Real receiver class not found: $realReceiverName", e)
        } catch (e: InstantiationException) {
            Logger.e(TAG, "Failed to instantiate receiver: $realReceiverName", e)
        } catch (e: IllegalAccessException) {
            Logger.e(TAG, "Failed to access receiver: $realReceiverName", e)
        } catch (e: Exception) {
            Logger.e(TAG, "Error dispatching to receiver: $realReceiverName", e)
        }
    }
}

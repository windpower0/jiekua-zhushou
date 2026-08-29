package cn.ppps.forwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.ppps.forwarder.activity.SplashActivity
import cn.ppps.forwarder.service.ForegroundService
import cn.ppps.forwarder.utils.ACTION_START
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.SettingUtils

@Suppress("PrivatePropertyName")
class BootCompletedReceiver : BroadcastReceiver() {

    private val TAG: String = BootCompletedReceiver::class.java.simpleName

    override fun onReceive(context: Context, intent: Intent?) {

        if (intent?.action != Intent.ACTION_BOOT_COMPLETED && intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        try {
            // 已配置过的接码助手：开机直接拉起前台服务常驻，无需打开界面即可持续转发短信
            if (SettingUtils.jkConfigured) {
                Log.d(TAG, "已配置，开机启动前台服务常驻")
                val svc = Intent(context, ForegroundService::class.java).apply { action = ACTION_START }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svc)
                } else {
                    context.startService(svc)
                }
                return
            }
            // 未配置：进入一键配置页
            Log.d(TAG, "强制重启APP一次")
            val intent1 = Intent(context, SplashActivity::class.java)
            intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent1)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "开机处理失败:${e.message}")
        }

    }
}
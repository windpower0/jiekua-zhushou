package cn.ppps.forwarder.activity

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import cn.ppps.forwarder.R

/**
 * 接码助手（二开）：后台保活引导页。
 * 列出各品牌开启「自启动 / 后台运行 / 电池白名单」的步骤，并提供一键跳系统设置入口。
 */
class KeepAliveGuideActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keepalive_guide)
        val tvGuide = findViewById<TextView>(R.id.tvGuide)
        val btnBattery = findViewById<Button>(R.id.btnBattery)
        val btnDetails = findViewById<Button>(R.id.btnDetails)

        tvGuide.text = buildGuide()

        btnBattery.setOnClickListener { requestBattery() }
        btnDetails.setOnClickListener { openAppDetails() }
    }

    private fun buildGuide(): String {
        val brand = Build.MANUFACTURER.lowercase()
        val common = """
            【通用必做】
            1. 锁定应用：最近任务里把接码助手下拉加锁（防止一键清理误杀）
            2. 允许自启动 / 允许后台运行
            3. 电池：设置为「不限制 / 无限制 / 允许后台活动」
            4. 通知：允许通知，且关闭「通知智能分类 / 静默通知」
            5. 关闭管家里的「应用联网控制 / 省电模式」对其限制

        """.trimIndent()

        val brandTip = when {
            brand.contains("huawei") || brand.contains("honor") ->
                "【华为 / 荣耀】手机管家 → 应用启动管理 → 接码助手设为「手动管理」并打开自启动/关联启动/后台活动；电池 → 应用省电管理 → 设为「不允许」。"
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ->
                "【小米 / Redmi】设置 → 应用设置 → 接码助手 → 自启动/后台运行/通知管理 全部开启；电量和性能 → 神隐模式 → 无限制；安全中心 → 授权管理 → 自启动管理 开启。"
            brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") ->
                "【OPPO / realme / 一加】设置 → 电池 → 应用耗电管理 → 接码助手 → 允许完全后台行为/不允许限制；设置 → 应用管理 → 接码助手 → 自启动/悬浮窗 开启。"
            brand.contains("vivo") || brand.contains("iqoo") ->
                "【vivo / iQOO】i管家 → 应用管理 → 接码助手 → 自启动/关联启动/后台运行 开启；电池 → 后台高耗电 → 加入白名单。"
            brand.contains("samsung") ->
                "【三星】设置 → 应用程序 → 接码助手 → 电池 → 不限制；设备保护/电池 → 自动运行应用 开启；设置 → 设备维护 → 自启动 开启。"
            else ->
                "【其它品牌】请在系统「设置 → 应用 → 接码助手」中开启：自启动、后台运行、电池无限制、允许通知，并将应用加入电池白名单。"
        }
        return common + brandTip
    }

    private fun requestBattery() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                i.data = Uri.parse("package:$packageName")
                startActivity(i)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openAppDetails() {
        try {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            i.data = Uri.parse("package:$packageName")
            startActivity(i)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

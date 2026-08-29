package cn.ppps.forwarder.activity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cn.ppps.forwarder.R
import cn.ppps.forwarder.service.ForegroundService
import cn.ppps.forwarder.utils.ACTION_START
import cn.ppps.forwarder.utils.SettingUtils
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import com.hjq.permissions.XXPermissions
import com.xuexiang.xutil.app.ActivityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 接码助手（二开）：首次启动的一键配置页。
 * 仅填平台 Token，授权后自动读取本机卡槽号码，自动建好 Webhook 通道与转发规则。
 * 申请短信读取 + 读取手机号权限，授权后即可自动填充号码（无需手动填写）。
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var etToken: EditText
    private lateinit var btnSetup: Button
    private lateinit var progress: ProgressBar
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        etToken = findViewById(R.id.etToken)
        btnSetup = findViewById(R.id.btnSetup)
        progress = findViewById(R.id.progress)
        tvStatus = findViewById(R.id.tvStatus)
        etToken.setText("wujian")
        btnSetup.setOnClickListener { onSetup() }
    }

    private fun onSetup() {
        val token = etToken.text.toString().trim()
        if (token.isEmpty()) {
            tvStatus.text = "请先填写接码平台 Token"
            return
        }
        progress.visibility = View.VISIBLE
        btnSetup.isEnabled = false
        tvStatus.text = "正在请求短信与读取手机号权限…"
        XXPermissions.with(this)
            .permission(PermissionLists.getReceiveSmsPermission())
            .permission(PermissionLists.getReadPhoneStatePermission())
            .permission(PermissionLists.getReadPhoneNumbersPermission())
            .request(object : OnPermissionCallback {
                override fun onResult(granted: MutableList<IPermission>, denied: MutableList<IPermission>) {
                    // 短信读取权限为必需；读取手机号权限用于自动填充本机号，被拒仍可继续（号码可能为空）
                    val smsDenied = denied.any { it.permission == PermissionLists.getReceiveSmsPermission().permission }
                    if (smsDenied) {
                        progress.visibility = View.GONE
                        btnSetup.isEnabled = true
                        tvStatus.text = "需要短信读取权限才能转发验证码，请在系统设置中授予后重试"
                        return
                    }
                    if (denied.isNotEmpty()) {
                        tvStatus.text = "未授予读取手机号权限，将使用卡槽标识（SIM1/SIM2）代替本机号"
                    }
                    buildConfig(token)
                }
            })
    }

    private fun buildConfig(token: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sims = readSims()
                AutoConfig.configure(token, sims)
                SettingUtils.jkToken = token
                SettingUtils.jkConfigured = true
                SettingUtils.enableSms = true
                startForwardService()
                runOnUiThread {
                    ActivityUtils.startActivity(MainActivity::class.java)
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    progress.visibility = View.GONE
                    btnSetup.isEnabled = true
                    tvStatus.text = "配置失败：${e.message}"
                }
            }
        }
    }

    /**
     * 读取本机卡槽号码。优先用 SubscriptionManager 逐卡读取，失败回退 TelephonyManager 主卡号。
     * 读取不到时返回空号（AutoConfig 会退化为 SIM1/SIM2 标识）。
     */
    private fun readSims(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        try {
            val sm = getSystemService(SubscriptionManager::class.java)
            val subs = sm.activeSubscriptionInfoList
            if (!subs.isNullOrEmpty()) {
                for ((i, sub) in subs.withIndex()) {
                    val label = "SIM" + (i + 1)
                    result.add(label to readNumber(sm, sub).orEmpty().trim())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (result.isEmpty()) {
            val num = try {
                val tm = getSystemService(TelephonyManager::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "" else tm.line1Number.orEmpty()
            } catch (e: Exception) {
                ""
            }
            result.add("SIM1" to num.trim())
        }
        return result
    }

    private fun readNumber(sm: SubscriptionManager, sub: SubscriptionInfo): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                sm.getPhoneNumber(sub.subscriptionId)
            } else {
                @Suppress("DEPRECATION")
                sub.number
            }
        } catch (e: Exception) {
            try {
                @Suppress("DEPRECATION")
                sub.number
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun startForwardService() {
        val intent = Intent(this, ForegroundService::class.java)
        intent.action = ACTION_START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

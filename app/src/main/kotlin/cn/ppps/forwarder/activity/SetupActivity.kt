package cn.ppps.forwarder.activity

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionManager
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
 * 仅要求填入平台 Token，自动读取卡槽手机号并建好 Webhook 通道与转发规则。
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
        tvStatus.text = "正在请求权限…"
        XXPermissions.with(this)
            .permission(PermissionLists.getReceiveSmsPermission())
            .permission(PermissionLists.getReadPhoneStatePermission())
            .permission(PermissionLists.getReadPhoneNumbersPermission())
            .permission(PermissionLists.getPostNotificationsPermission())
            .request(object : OnPermissionCallback {
                override fun onResult(granted: MutableList<IPermission>, denied: MutableList<IPermission>) {
                    if (denied.isNotEmpty()) {
                        progress.visibility = View.GONE
                        btnSetup.isEnabled = true
                        tvStatus.text = "权限被拒绝，无法读取短信/卡槽，请在系统设置中授予后重试"
                        return
                    }
                    buildConfig(token)
                }
            })
    }

    private fun buildConfig(token: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sims = readSims(this@SetupActivity)
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

    private fun startForwardService() {
        val intent = Intent(this, ForegroundService::class.java)
        intent.action = ACTION_START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun readSims(ctx: Context): List<Pair<String, String>> {
        return try {
            val sm = ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val list = sm.activeSubscriptionInfoList ?: emptyList()
            if (list.isEmpty()) {
                listOf("SIM1" to "")
            } else {
                list.mapIndexed { index, sub ->
                    val label = "SIM${index + 1}"
                    val number = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try {
                            sm.getPhoneNumber(sub.subscriptionId)
                        } catch (_: Exception) {
                            ""
                        }
                    } else {
                        sub.number ?: ""
                    }
                    label to number
                }
            }
        } catch (_: Exception) {
            listOf("SIM1" to "")
        }
    }
}

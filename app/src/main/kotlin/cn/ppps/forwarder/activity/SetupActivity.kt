package cn.ppps.forwarder.activity

import android.content.Intent
import android.os.Build
import android.os.Bundle
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
 * 仅要求填入平台 Token 与本机号码（无法自动读取时手动填），自动建好 Webhook 通道与转发规则。
 * 只申请短信读取权限，不申请 READ_PHONE_STATE，因此号码需用户手动填写。
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var etToken: EditText
    private lateinit var etSim1: EditText
    private lateinit var etSim2: EditText
    private lateinit var btnSetup: Button
    private lateinit var progress: ProgressBar
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        etToken = findViewById(R.id.etToken)
        etSim1 = findViewById(R.id.etSim1)
        etSim2 = findViewById(R.id.etSim2)
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
        val sim1 = etSim1.text.toString().trim()
        if (sim1.isEmpty()) {
            tvStatus.text = "请填写卡槽1 本机号码"
            return
        }
        progress.visibility = View.VISIBLE
        btnSetup.isEnabled = false
        tvStatus.text = "正在请求短信权限…"
        XXPermissions.with(this)
            .permission(PermissionLists.getReceiveSmsPermission())
            .request(object : OnPermissionCallback {
                override fun onResult(granted: MutableList<IPermission>, denied: MutableList<IPermission>) {
                    // 仅短信读取权限为必需；其余权限可选，被拒不影响配置
                    if (denied.isNotEmpty()) {
                        progress.visibility = View.GONE
                        btnSetup.isEnabled = true
                        tvStatus.text = "需要短信读取权限才能转发验证码，请在系统设置中授予后重试"
                        return
                    }
                    buildConfig(token, sim1, etSim2.text.toString().trim())
                }
            })
    }

    private fun buildConfig(token: String, sim1: String, sim2: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sims = mutableListOf("SIM1" to sim1)
                if (sim2.isNotEmpty()) sims.add("SIM2" to sim2)
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
}

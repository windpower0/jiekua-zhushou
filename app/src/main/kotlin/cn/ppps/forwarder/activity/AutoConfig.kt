package cn.ppps.forwarder.activity

import cn.ppps.forwarder.App
import cn.ppps.forwarder.database.entity.Rule
import cn.ppps.forwarder.database.entity.Sender
import cn.ppps.forwarder.database.entity.setting.WebhookSetting
import cn.ppps.forwarder.utils.JK_SERVER_URL
import cn.ppps.forwarder.utils.STATUS_ON
import cn.ppps.forwarder.utils.TYPE_WEBHOOK
import com.google.gson.Gson
import java.util.Date

/**
 * 接码助手（二开）：根据平台 Token 与卡槽列表，程序化创建 Webhook 发送通道 + 转发规则。
 * 模板固定为：{"mark":"SIMx","phone":"<真实号>","sender":"[from]","content":"[content]"}
 */
object AutoConfig {

    fun configure(token: String, sims: List<Pair<String, String>>) {
        if (sims.isEmpty()) return
        for ((label, number) in sims) {
            val body = LinkedHashMap<String, String>()
            body["mark"] = label
            if (number.isNotBlank()) body["phone"] = number
            body["sender"] = "[from]"
            body["content"] = "[content]"
            val setting = WebhookSetting(
                method = "POST",
                webServer = JK_SERVER_URL,
                webParams = Gson().toJson(body),
                headers = mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json",
                ),
            )
            val sender = Sender(0L, TYPE_WEBHOOK, "接码-$label", Gson().toJson(setting), STATUS_ON)
            App.senderRepository.insert(sender)
            val created = App.senderRepository.getAllNonCache().last()
            val rule = Rule(
                0L, "sms", "transpond_all", "is", "",
                created.id, "", "",
                label, STATUS_ON, Date(), listOf(created), "ALL",
                0, 0, "", "接码规则-$label",
            )
            App.ruleRepository.insert(rule)
        }
    }
}

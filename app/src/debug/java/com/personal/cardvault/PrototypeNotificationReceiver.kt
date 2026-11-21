package com.personal.cardvault

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** T1.9 专用：只验证通知触发的广播动作，绝不从这里启动页面。 */
class PrototypeNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.data?.lastPathSegment ?: "未知动作"
        Toast.makeText(context, "测试广播已收到：$action", Toast.LENGTH_SHORT).show()
    }
}

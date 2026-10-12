package com.wslee.appinout

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast

class InOutWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_inout)
            views.setOnClickPendingIntent(R.id.btn_in, pending(context, ACTION_IN, 1))
            views.setOnClickPendingIntent(R.id.btn_out, pending(context, ACTION_OUT, 2))
            manager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // 이전 버전에서 만들어진 위젯 버튼(브로드캐스트 방식)이 눌린 경우:
        // 기록은 그대로 하고, 버튼을 새 팝업 방식으로 바꿔 둔다.
        val result = when (intent.action) {
            ACTION_IN -> AttendanceLog.clockIn(context)
            ACTION_OUT -> AttendanceLog.clockOut(context)
            else -> return
        }
        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
        val manager = AppWidgetManager.getInstance(context)
        onUpdate(context, manager, manager.getAppWidgetIds(ComponentName(context, InOutWidget::class.java)))
    }

    private fun pending(context: Context, action: String, code: Int): PendingIntent {
        val intent = Intent(context, FeedbackActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        return PendingIntent.getActivity(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_IN = "com.wslee.appinout.ACTION_IN"
        const val ACTION_OUT = "com.wslee.appinout.ACTION_OUT"
    }
}

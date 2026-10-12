package com.wslee.appinout

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
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
        val msg = when (intent.action) {
            ACTION_IN -> AttendanceLog.clockIn(context)
            ACTION_OUT -> AttendanceLog.clockOut(context)
            else -> return
        }
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    private fun pending(context: Context, action: String, code: Int): PendingIntent {
        val intent = Intent(context, InOutWidget::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_IN = "com.wslee.appinout.ACTION_IN"
        const val ACTION_OUT = "com.wslee.appinout.ACTION_OUT"
    }
}

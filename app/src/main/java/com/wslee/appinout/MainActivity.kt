package com.wslee.appinout

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class MainActivity : Activity() {

    private lateinit var logText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        logText = findViewById(R.id.log_text)
        applySystemBarPadding(findViewById(R.id.root))

        findViewById<Button>(R.id.btn_manual_in).setOnClickListener {
            val c = Calendar.getInstance()
            TimePickerDialog(this, { _, h, m ->
                val r = AttendanceLog.setClockInManually(this, h, m)
                Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                refresh()
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
        }

        findViewById<Button>(R.id.btn_day_off).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("오늘(${AttendanceLog.todayLabel()})을\n${AttendanceLog.DAY_OFF_LABEL}로 기록할까요?")
                .setPositiveButton("기록") { _, _ ->
                    val r = AttendanceLog.markDayOff(this)
                    Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                    refresh()
                }
                .setNegativeButton("취소", null)
                .show()
        }

        findViewById<Button>(R.id.btn_share).setOnClickListener {
            val content = AttendanceLog.read(this)
            if (content.isBlank()) {
                Toast.makeText(this, "아직 기록이 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, content)
            }
            startActivity(Intent.createChooser(send, "기록 공유"))
        }

        findViewById<Button>(R.id.btn_open).setOnClickListener {
            val uri = AttendanceLog.findOwnFile(this)
            if (uri == null) {
                Toast.makeText(this, "아직 기록 파일이 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(view)
            } catch (e: Exception) {
                Toast.makeText(this, "파일을 열 앱이 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 상태바/내비게이션바에 내용이 가려지지 않도록 여백 추가 (Android 15 전체화면 대응) */
    private fun applySystemBarPadding(root: View) {
        val base = root.paddingTop
        root.setOnApplyWindowInsetsListener { v, insets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                top = bars.top; bottom = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }
            v.setPadding(v.paddingLeft, base + top, v.paddingRight, base + bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val content = AttendanceLog.read(this)
        logText.text = content.ifBlank { "아직 기록이 없습니다." }
    }
}

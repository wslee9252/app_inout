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

        findViewById<Button>(R.id.btn_edit_in).setOnClickListener {
            val target = AttendanceLog.clockInEditTarget(this)
            val title = target?.first ?: "${AttendanceLog.todayLabel()} 출근 기록 (깜빡했을 때)"
            val c = Calendar.getInstance()
            val init = target?.second ?: (c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE))
            pickTime(title, init) { h, m -> AttendanceLog.editClockIn(this, h, m) }
        }

        findViewById<Button>(R.id.btn_edit_out).setOnClickListener {
            val target = AttendanceLog.clockOutEditTarget(this)
            if (target == null) {
                Toast.makeText(this, "수정할 퇴근 기록이 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pickTime(target.first, target.second) { h, m -> AttendanceLog.editClockOut(this, h, m) }
        }

        bindLeave(R.id.btn_leave_full, AttendanceLog.LEAVE_FULL)
        bindLeave(R.id.btn_leave_am, AttendanceLog.LEAVE_AM)
        bindLeave(R.id.btn_leave_pm, AttendanceLog.LEAVE_PM)

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

    private fun pickTime(title: String, initMin: Int, action: (Int, Int) -> AttendanceLog.Result) {
        val dlg = TimePickerDialog(this, { _, h, m ->
            val r = action(h, m)
            Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
            refresh()
        }, initMin / 60, initMin % 60, true)
        dlg.setTitle(title)
        dlg.show()
    }

    private fun bindLeave(buttonId: Int, label: String) {
        findViewById<Button>(buttonId).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("오늘(${AttendanceLog.todayLabel()})을\n${label}로 기록할까요?")
                .setPositiveButton("기록") { _, _ ->
                    val r = AttendanceLog.markLeave(this, label)
                    Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                    refresh()
                }
                .setNegativeButton("취소", null)
                .show()
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

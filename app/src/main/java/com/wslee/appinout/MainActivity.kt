package com.wslee.appinout

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var logText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        logText = findViewById(R.id.log_text)

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

    override fun onResume() {
        super.onResume()
        val content = AttendanceLog.read(this)
        logText.text = content.ifBlank { "아직 기록이 없습니다." }
    }
}

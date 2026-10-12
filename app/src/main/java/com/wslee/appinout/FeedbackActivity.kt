package com.wslee.appinout

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 위젯 버튼을 누르면 실행되는 투명 화면.
 * 기록을 남긴 뒤 화면 중앙에 반투명 팝업을 잠깐 보여주고 스스로 닫힌다.
 */
class FeedbackActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 화면 회전 등으로 다시 만들어질 때 중복 기록 방지
        val result = savedInstanceState?.getString(KEY_MSG)?.let { AttendanceLog.Result(true, it) }
            ?: when (intent.action) {
                InOutWidget.ACTION_IN -> AttendanceLog.clockIn(this)
                InOutWidget.ACTION_OUT -> AttendanceLog.clockOut(this)
                else -> { finish(); return }
            }
        message = result.message

        val dp = { v: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics) }
        val card = TextView(this).apply {
            text = result.message
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            val ph = dp(28f).toInt(); val pv = dp(20f).toInt()
            setPadding(ph, pv, ph, pv)
            background = GradientDrawable().apply {
                cornerRadius = dp(20f)
                setColor(if (result.ok) Color.argb(200, 20, 20, 20) else Color.argb(210, 150, 20, 20))
            }
        }
        val root = FrameLayout(this).apply {
            addView(card, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ))
            setOnClickListener { finish() } // 터치하면 바로 닫힘
        }
        setContentView(root)
        handler.postDelayed({ finish() }, 1500)
    }

    private var message: String = ""

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_MSG, message)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    companion object {
        private const val KEY_MSG = "msg"
    }
}

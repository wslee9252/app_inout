package com.wslee.appinout

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 출퇴근 기록을 Download/출퇴근기록.txt 에 저장한다.
 * 앱 내부에도 같은 내용을 백업해 두고, 파일을 못 읽을 때 백업을 사용한다.
 */
object AttendanceLog {

    const val FILE_NAME = "출퇴근기록.txt"
    private val RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/"
    private const val PREFS = "inout"
    private const val KEY_OPEN_IN = "open_in_millis"
    private const val BACKUP_NAME = "log_backup.txt"

    private val dateFmt get() = SimpleDateFormat("yyyy-MM-dd (E)", Locale.KOREAN)
    private val dayKeyFmt get() = SimpleDateFormat("yyyyMMdd", Locale.KOREAN)
    private val timeFmt get() = SimpleDateFormat("HH:mm", Locale.KOREAN)

    /** 출근 버튼. 결과 메시지를 돌려준다. */
    fun clockIn(context: Context): String {
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val openIn = prefs.getLong(KEY_OPEN_IN, 0L)
        if (openIn != 0L && sameDay(openIn, now)) {
            return "이미 출근 기록이 있습니다. (출근 : ${timeFmt.format(Date(openIn))})"
        }

        val current = read(context)
        val sb = StringBuilder(current)
        if (sb.isNotEmpty()) {
            if (!sb.endsWith("\n")) sb.append("\n")
            sb.append("\n") // 날짜 블록 사이 빈 줄
        }
        sb.append(dateFmt.format(Date(now))).append("\n")
        sb.append("출근 : ").append(timeFmt.format(Date(now))).append("\n")

        write(context, sb.toString())
        prefs.edit().putLong(KEY_OPEN_IN, now).apply()
        return "출근 : ${dateFmt.format(Date(now))}, ${timeFmt.format(Date(now))}"
    }

    /** 퇴근 버튼. 같은 날짜의 출근 기록이 없으면 기록하지 않는다. */
    fun clockOut(context: Context): String {
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val openIn = prefs.getLong(KEY_OPEN_IN, 0L)
        if (openIn == 0L || !sameDay(openIn, now)) {
            return "출근 이력이없습니다."
        }

        // 화면에 표시되는 분 단위 시각 기준으로 계산 (08:52 ~ 18:07 = 9시간 15분)
        val minutes = (now / 60_000L) - (openIn / 60_000L)
        val work = "${minutes / 60}시간 ${minutes % 60}분"

        val sb = StringBuilder(read(context))
        if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
        sb.append("퇴근 : ").append(timeFmt.format(Date(now))).append("\n")
        sb.append("근무 : ").append(work).append("\n")

        write(context, sb.toString())
        prefs.edit().putLong(KEY_OPEN_IN, 0L).apply()
        return "퇴근 : ${dateFmt.format(Date(now))}, ${timeFmt.format(Date(now))}\n근무 : $work"
    }

    fun read(context: Context): String {
        val uri = findOwnFile(context)
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use {
                    return it.readBytes().toString(Charsets.UTF_8)
                }
            } catch (e: Exception) {
                // 읽기 실패 시 백업 사용
            }
        }
        val backup = File(context.filesDir, BACKUP_NAME)
        return if (backup.exists()) backup.readText(Charsets.UTF_8) else ""
    }

    /** 다운로드 폴더에 있는 기록 파일의 Uri (없으면 null) */
    fun findOwnFile(context: Context): Uri? {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
            arrayOf(FILE_NAME, RELATIVE_PATH),
            null
        )?.use { c ->
            if (c.moveToFirst()) {
                return Uri.withAppendedPath(collection, c.getLong(0).toString())
            }
        }
        return null
    }

    private fun write(context: Context, content: String) {
        File(context.filesDir, BACKUP_NAME).writeText(content, Charsets.UTF_8)

        val bytes = content.toByteArray(Charsets.UTF_8)
        val existing = findOwnFile(context)
        if (existing != null) {
            try {
                context.contentResolver.openOutputStream(existing, "wt")?.use { it.write(bytes) }
                return
            } catch (e: Exception) {
                // 아래에서 새 파일로 다시 만든다
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
    }

    private fun sameDay(a: Long, b: Long): Boolean =
        dayKeyFmt.format(Date(a)) == dayKeyFmt.format(Date(b))
}

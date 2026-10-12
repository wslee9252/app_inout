package com.wslee.appinout

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 출퇴근 기록을 Download/출퇴근기록.txt 에 저장한다.
 * 출근 여부는 txt 파일 내용을 직접 읽어서 판단하므로,
 * 파일에 출근 시간을 손으로 적어 넣어도 퇴근 시 근무 시간이 계산된다.
 */
object AttendanceLog {

    const val FILE_NAME = "출퇴근기록.txt"
    private val RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/"
    private const val BACKUP_NAME = "log_backup.txt"

    private val dateFmt get() = SimpleDateFormat("yyyy-MM-dd (E)", Locale.KOREAN)
    private val dayFmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
    private val timeFmt get() = SimpleDateFormat("HH:mm", Locale.KOREAN)

    private val DATE_LINE = Regex("""^\s*(\d{4}-\d{2}-\d{2})""")
    private val IN_LINE = Regex("""^\s*출근\s*:?\s*(\d{1,2})\s*:\s*(\d{2})""")
    private val OUT_LINE = Regex("""^\s*퇴근""")

    data class Result(val ok: Boolean, val message: String)

    /** 오늘 날짜 블록 중 퇴근이 아직 없는 출근 시각(하루 중 분). 없으면 null */
    private fun openClockIn(content: String, today: String): Int? {
        val lines = content.lines()
        var blockDate: String? = null
        var inMinutes: Int? = null
        for (line in lines) {
            val d = DATE_LINE.find(line)
            if (d != null) {
                blockDate = d.groupValues[1]
                inMinutes = null
                continue
            }
            if (blockDate != today) continue
            val m = IN_LINE.find(line)
            if (m != null) {
                val h = m.groupValues[1].toInt()
                val min = m.groupValues[2].toInt()
                if (h in 0..23 && min in 0..59) inMinutes = h * 60 + min
                continue
            }
            if (OUT_LINE.containsMatchIn(line)) inMinutes = null
        }
        return if (blockDate == today) inMinutes else null
    }

    private fun minutesOfDay(now: Date): Int {
        val c = Calendar.getInstance().apply { time = now }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    private fun hhmm(minutes: Int) = String.format(Locale.KOREAN, "%02d:%02d", minutes / 60, minutes % 60)

    /** 출근 버튼 */
    fun clockIn(context: Context): Result {
        val now = Date()
        val content = read(context)
        val open = openClockIn(content, dayFmt.format(now))
        if (open != null) {
            return Result(false, "이미 출근 기록이 있습니다. (출근 : ${hhmm(open)})")
        }
        write(context, appendBlock(content, now, timeFmt.format(now)))
        return Result(true, "✅ 출근완료\n${dateFmt.format(now)} ${timeFmt.format(now)}")
    }

    /** 앱 화면에서 출근 시간을 직접 입력 (깜빡하고 안 눌렀을 때) */
    fun setClockInManually(context: Context, hour: Int, minute: Int): Result {
        val now = Date()
        val time = hhmm(hour * 60 + minute)
        if (hour * 60 + minute > minutesOfDay(now)) {
            return Result(false, "현재 시각보다 늦은 출근 시간은 입력할 수 없습니다.")
        }
        val content = read(context)
        val today = dayFmt.format(now)
        if (openClockIn(content, today) != null) {
            // 오늘 열린 출근 기록의 시간을 고친다 (마지막 출근 줄)
            val lines = content.lines().toMutableList()
            val idx = lines.indexOfLast { IN_LINE.containsMatchIn(it) }
            if (idx >= 0) lines[idx] = "출근 : $time"
            write(context, lines.joinToString("\n"))
        } else {
            write(context, appendBlock(content, now, time))
        }
        return Result(true, "출근 시간을 $time 으로 기록했습니다.")
    }

    /** 퇴근 버튼. 같은 날짜의 출근 기록이 없으면 기록하지 않는다. */
    fun clockOut(context: Context): Result {
        val now = Date()
        val content = read(context)
        val open = openClockIn(content, dayFmt.format(now))
            ?: return Result(false, "출근 이력이없습니다.")

        val minutes = (minutesOfDay(now) - open).coerceAtLeast(0)
        val work = "${minutes / 60}시간 ${minutes % 60}분"

        val sb = StringBuilder(content)
        if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
        sb.append("퇴근 : ").append(timeFmt.format(now)).append("\n")
        sb.append("근무 : ").append(work).append("\n")
        write(context, sb.toString())
        return Result(true, "✅ 퇴근완료\n${timeFmt.format(now)} · 근무 $work")
    }

    private fun appendBlock(content: String, now: Date, time: String): String {
        val sb = StringBuilder(content.trimEnd())
        if (sb.isNotEmpty()) sb.append("\n\n") // 날짜 블록 사이 빈 줄
        sb.append(dateFmt.format(now)).append("\n")
        sb.append("출근 : ").append(time).append("\n")
        return sb.toString()
    }

    fun read(context: Context): String {
        val uri = findOwnFile(context)
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use {
                    return it.readBytes().toString(Charsets.UTF_8).removePrefix("﻿")
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
}

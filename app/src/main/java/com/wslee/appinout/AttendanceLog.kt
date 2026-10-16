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
 * 출퇴근 기록을 Download/출퇴근기록.txt 에 저장한다. 기록의 기준은 이 파일 하나뿐이다.
 * 출근 여부는 txt 파일 내용을 직접 읽어서 판단하므로,
 * 파일에 출근 시간을 손으로 적어 넣어도 퇴근 시 근무 시간이 계산된다.
 *
 * 점심시간 11:45~12:45
 *  - 출근 괄호: 11:45 - 출근시각 (오전 근무)
 *  - 퇴근 괄호: 퇴근시각 - 12:45 (오후 근무)
 *    단, 11:45 이전 퇴근 또는 12:45 이후 출근이면 퇴근시각 - 출근시각
 *  - 근무: 출근~퇴근 중 점심시간을 뺀 시간
 *  - 괄호 값이 음수면 괄호 생략
 */
object AttendanceLog {

    const val FILE_NAME = "출퇴근기록.txt"
    private val RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/"
    private const val BACKUP_NAME = "log_backup.txt"

    private const val LUNCH_START = 11 * 60 + 45
    private const val LUNCH_END = 12 * 60 + 45

    const val LEAVE_FULL = "전일 휴가"
    const val LEAVE_AM = "오전반차"
    const val LEAVE_PM = "오후반차"
    private const val OLD_DAY_OFF = "공휴일/휴가" // 이전 버전 표기
    private val ALL_LEAVES = listOf(LEAVE_FULL, LEAVE_AM, LEAVE_PM, OLD_DAY_OFF)
    private val FULL_DAY_LEAVES = listOf(LEAVE_FULL, OLD_DAY_OFF)

    private val dateFmt get() = SimpleDateFormat("yyyy-MM-dd (E)", Locale.KOREAN)
    private val dayFmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)

    private val DATE_LINE = Regex("""^\s*(\d{4}-\d{2}-\d{2})""")
    private val IN_LINE = Regex("""^\s*출근\s*:?\s*(\d{1,2})\s*:\s*(\d{2})""")
    private val OUT_LINE = Regex("""^\s*퇴근""")

    data class Result(val ok: Boolean, val message: String)

    // ---------- 시간 계산 ----------

    private fun hhmm(minutes: Int) = String.format(Locale.KOREAN, "%02d:%02d", minutes / 60, minutes % 60)
    private fun dur(minutes: Int) = "${minutes / 60}시간 ${minutes % 60}분"

    private fun minutesOfDay(now: Date): Int {
        val c = Calendar.getInstance().apply { time = now }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    /** 출근 괄호: (오전 근무 끝 - 출근). 오전 근무 끝 = 11:45, 단 11:45 전에 퇴근하면 퇴근시각 */
    private fun inLine(inMin: Int, outMin: Int? = null): String {
        val amEnd = if (outMin != null) minOf(outMin, LUNCH_START) else LUNCH_START
        val am = amEnd - inMin
        return "출근 : ${hhmm(inMin)}" + if (am >= 0) " (${dur(am)})" else ""
    }

    /**
     * 퇴근 괄호
     *  - 11:45 이전(정각 포함) 퇴근: 퇴근 - 출근 (오전만 근무)
     *  - 12:45 이후 출근: 퇴근 - 출근 (오후만 근무)
     *  - 그 외: 퇴근 - 12:45 (점심시간 중 출근도 동일, 점심은 근무에서 빠지므로)
     */
    private fun outLine(inMin: Int, outMin: Int): String {
        val v = if (outMin <= LUNCH_START) outMin - inMin else outMin - maxOf(inMin, LUNCH_END)
        return "퇴근 : ${hhmm(outMin)}" + if (v >= 0) " (${dur(v)})" else ""
    }

    /** 출근~퇴근 중 점심시간(11:45~12:45)과 겹치는 부분을 뺀 근무 시간(분) */
    private fun workMinutes(inMin: Int, outMin: Int): Int {
        val total = (outMin - inMin).coerceAtLeast(0)
        val overlap = (minOf(outMin, LUNCH_END) - maxOf(inMin, LUNCH_START)).coerceAtLeast(0)
        return total - overlap
    }

    // ---------- 파일 내용 분석 ----------

    private class Today(
        val openIn: Int?,            // 퇴근이 아직 없는 출근 시각(분)
        val leave: String?,          // 오늘 기록된 휴가 종류
        val hasClockIn: Boolean,     // 오늘 출근 기록이 하나라도 있는지
        val lastDateLineIdx: Int,    // 오늘 날짜 줄 중 마지막 줄 번호 (-1: 없음)
        val lastBlockIsToday: Boolean,
        val lastBlockHasIn: Boolean
    )

    private fun analyze(lines: List<String>, today: String): Today {
        var blockDate: String? = null
        var openIn: Int? = null
        var leave: String? = null
        var hasClockIn = false
        var lastDateLineIdx = -1
        var blockHasIn = false
        for ((i, line) in lines.withIndex()) {
            val d = DATE_LINE.find(line)
            if (d != null) {
                blockDate = d.groupValues[1]
                openIn = null
                blockHasIn = false
                if (blockDate == today) {
                    lastDateLineIdx = i
                    ALL_LEAVES.firstOrNull { line.contains(it) }?.let { leave = it }
                }
                continue
            }
            val m = IN_LINE.find(line)
            if (m != null) {
                blockHasIn = true
                if (blockDate == today) {
                    hasClockIn = true
                    val h = m.groupValues[1].toInt()
                    val min = m.groupValues[2].toInt()
                    if (h in 0..23 && min in 0..59) openIn = h * 60 + min
                }
                continue
            }
            if (OUT_LINE.containsMatchIn(line)) openIn = null
        }
        val lastIsToday = blockDate == today
        return Today(
            openIn = if (lastIsToday) openIn else null,
            leave = leave,
            hasClockIn = hasClockIn,
            lastDateLineIdx = lastDateLineIdx,
            lastBlockIsToday = lastIsToday,
            lastBlockHasIn = lastIsToday && blockHasIn
        )
    }

    // ---------- 버튼 동작 ----------

    /** 출근 버튼 */
    fun clockIn(context: Context): Result {
        val now = Date()
        val nowMin = minutesOfDay(now)
        val content = read(context)
        val t = analyze(content.lines(), dayFmt.format(now))
        if (t.openIn != null) {
            return Result(false, "이미 출근 기록이 있습니다. (출근 : ${hhmm(t.openIn)})")
        }
        if (t.leave in FULL_DAY_LEAVES) {
            return Result(false, "오늘은 ${t.leave}로 기록되어 있습니다.")
        }
        val newContent = if (t.lastBlockIsToday && !t.lastBlockHasIn) {
            // 반차 등으로 오늘 날짜 줄만 있는 경우 그 아래에 출근 추가
            content.trimEnd() + "\n" + inLine(nowMin) + "\n"
        } else {
            appendBlock(content, now, inLine(nowMin))
        }
        write(context, newContent)
        return Result(true, "✅ 출근완료\n${dateFmt.format(now)} ${hhmm(nowMin)}")
    }

    // ---------- 출근/퇴근 시간 수정 (가장 최근 기록 대상) ----------

    /** 한 번의 출근~퇴근 기록 (줄 번호) */
    private class Rec(
        val date: String,        // yyyy-MM-dd
        val dateLine: String,    // 날짜 줄 원문 (예: 2026-10-16 (금) 오전반차)
        val inIdx: Int,
        val inMin: Int,
        var outIdx: Int = -1,
        var outMin: Int = -1,
        var workIdx: Int = -1
    )

    private val OUT_TIME = Regex("""^\s*퇴근\s*:?\s*(\d{1,2})\s*:\s*(\d{2})""")
    private val WORK_LINE = Regex("""^\s*근무""")

    private fun records(lines: List<String>): List<Rec> {
        val list = mutableListOf<Rec>()
        var date: String? = null
        var dateLine = ""
        var cur: Rec? = null
        for ((i, line) in lines.withIndex()) {
            val d = DATE_LINE.find(line)
            if (d != null) {
                date = d.groupValues[1]; dateLine = line.trim(); cur = null
                continue
            }
            if (date == null) continue
            val m = IN_LINE.find(line)
            if (m != null) {
                val min = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
                cur = Rec(date, dateLine, i, min).also { list.add(it) }
                continue
            }
            val c = cur ?: continue
            val o = OUT_TIME.find(line)
            if (o != null && c.outIdx < 0) {
                c.outIdx = i
                c.outMin = o.groupValues[1].toInt() * 60 + o.groupValues[2].toInt()
                continue
            }
            if (WORK_LINE.containsMatchIn(line) && c.outIdx >= 0 && c.workIdx < 0) c.workIdx = i
        }
        return list
    }

    /** 수정 대상 안내: (화면에 보여줄 제목, 현재 기록된 시간) — 대상이 없으면 null */
    fun clockInEditTarget(context: Context): Pair<String, Int>? {
        val today = dayFmt.format(Date())
        val rec = records(read(context).lines()).lastOrNull { it.date == today } ?: return null
        return Pair("${rec.dateLine} 출근 시간 수정", rec.inMin)
    }

    fun clockOutEditTarget(context: Context): Pair<String, Int>? {
        val rec = records(read(context).lines()).lastOrNull { it.outIdx >= 0 } ?: return null
        return Pair("${rec.dateLine} 퇴근 시간 수정", rec.outMin)
    }

    /** 출근 시간 수정. 오늘 출근 기록이 없으면 오늘 출근으로 새로 기록한다 (깜빡한 경우). */
    fun editClockIn(context: Context, hour: Int, minute: Int): Result {
        val now = Date()
        val nowMin = minutesOfDay(now)
        val inMin = hour * 60 + minute
        val today = dayFmt.format(now)
        val content = read(context)
        val lines = content.lines().toMutableList()
        val rec = records(lines).lastOrNull { it.date == today }

        if (rec == null) {
            val t = analyze(lines, today)
            if (t.leave in FULL_DAY_LEAVES) return Result(false, "오늘은 ${t.leave}로 기록되어 있습니다.")
            if (inMin > nowMin) return Result(false, "현재 시각보다 늦은 출근 시간은 입력할 수 없습니다.")
            val newContent = if (t.lastBlockIsToday && !t.lastBlockHasIn) {
                content.trimEnd() + "\n" + inLine(inMin) + "\n"
            } else {
                appendBlock(content, now, inLine(inMin))
            }
            write(context, newContent)
            return Result(true, "오늘 출근을 ${hhmm(inMin)} 으로 기록했습니다.")
        }

        if (rec.outIdx >= 0) {
            if (inMin >= rec.outMin) return Result(false, "출근 시간은 퇴근 시간(${hhmm(rec.outMin)})보다 빨라야 합니다.")
        } else if (inMin > nowMin) {
            return Result(false, "현재 시각보다 늦은 출근 시간은 입력할 수 없습니다.")
        }
        applyRec(lines, rec, inMin, if (rec.outIdx >= 0) rec.outMin else null)
        write(context, lines.joinToString("\n"))
        return Result(true, "출근 시간을 ${hhmm(inMin)} 으로 수정했습니다.")
    }

    /** 퇴근 시간 수정 (가장 최근 퇴근 기록) */
    fun editClockOut(context: Context, hour: Int, minute: Int): Result {
        val now = Date()
        val outMin = hour * 60 + minute
        val lines = read(context).lines().toMutableList()
        val rec = records(lines).lastOrNull { it.outIdx >= 0 }
            ?: return Result(false, "수정할 퇴근 기록이 없습니다.")
        if (outMin <= rec.inMin) return Result(false, "퇴근 시간은 출근 시간(${hhmm(rec.inMin)})보다 늦어야 합니다.")
        if (rec.date == dayFmt.format(now) && outMin > minutesOfDay(now)) {
            return Result(false, "현재 시각보다 늦은 퇴근 시간은 입력할 수 없습니다.")
        }
        applyRec(lines, rec, rec.inMin, outMin)
        write(context, lines.joinToString("\n"))
        return Result(true, "퇴근 시간을 ${hhmm(outMin)} 으로 수정했습니다.\n근무 : ${dur(workMinutes(rec.inMin, outMin))}")
    }

    /** 기록 한 건의 출근/퇴근 줄과 근무 시간을 새 값으로 다시 쓴다 */
    private fun applyRec(lines: MutableList<String>, rec: Rec, inMin: Int, outMin: Int?) {
        lines[rec.inIdx] = inLine(inMin, outMin)
        if (outMin == null || rec.outIdx < 0) return
        lines[rec.outIdx] = outLine(inMin, outMin)
        val work = "근무 : ${dur(workMinutes(inMin, outMin))}"
        if (rec.workIdx >= 0) lines[rec.workIdx] = work else lines.add(rec.outIdx + 1, work)
    }

    /** 퇴근 버튼. 같은 날짜의 출근 기록이 없으면 기록하지 않는다. */
    fun clockOut(context: Context): Result {
        val now = Date()
        val outMin = minutesOfDay(now)
        val content = read(context)
        val open = analyze(content.lines(), dayFmt.format(now)).openIn
            ?: return Result(false, "출근 이력이없습니다.")

        val work = dur(workMinutes(open, outMin))
        // 11:45 전에 퇴근하면 출근 괄호를 실제 오전 근무 시간으로 고친다
        var base = content
        if (outMin < LUNCH_START) {
            val lines = content.lines().toMutableList()
            val idx = lines.indexOfLast { IN_LINE.containsMatchIn(it) }
            if (idx >= 0) {
                lines[idx] = inLine(open, outMin)
                base = lines.joinToString("\n")
            }
        }
        val sb = StringBuilder(base)
        if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
        sb.append(outLine(open, outMin)).append("\n")
        sb.append("근무 : ").append(work).append("\n")
        write(context, sb.toString())
        return Result(true, "✅ 퇴근완료\n${hhmm(outMin)} · 근무 $work")
    }

    /** 앱 화면의 휴가 버튼 (전일 휴가 / 오전반차 / 오후반차) */
    fun markLeave(context: Context, label: String): Result {
        val now = Date()
        val content = read(context)
        val lines = content.lines().toMutableList()
        val t = analyze(lines, dayFmt.format(now))
        if (t.leave != null) {
            return Result(false, "오늘은 이미 ${t.leave}로 기록되어 있습니다.")
        }
        if (label == LEAVE_FULL && t.hasClockIn) {
            return Result(false, "오늘은 출근 기록이 있어 전일 휴가로 기록하지 않았습니다.")
        }
        val newContent = if (t.lastDateLineIdx >= 0) {
            // 오늘 날짜 줄 끝에 표기 (예: 2026-10-15 (목) 오후반차)
            lines[t.lastDateLineIdx] = lines[t.lastDateLineIdx].trimEnd() + " " + label
            lines.joinToString("\n")
        } else {
            appendBlock(content, now, null, label)
        }
        write(context, newContent)
        return Result(true, "${dateFmt.format(now)} $label 로 기록했습니다.")
    }

    fun todayLabel(): String = dateFmt.format(Date())

    private fun appendBlock(content: String, now: Date, firstLine: String?, dateSuffix: String? = null): String {
        val sb = StringBuilder(content.trimEnd())
        if (sb.isNotEmpty()) sb.append("\n\n") // 날짜 블록 사이 빈 줄
        sb.append(dateFmt.format(now))
        if (dateSuffix != null) sb.append(" ").append(dateSuffix)
        sb.append("\n")
        if (firstLine != null) sb.append(firstLine).append("\n")
        return sb.toString()
    }

    // ---------- 파일 읽기/쓰기 ----------

    /** txt 파일 내용. 파일이 없으면(삭제했으면) 빈 기록으로 본다. */
    fun read(context: Context): String {
        // 이전 버전이 만들던 내부 백업은 더 이상 쓰지 않으므로 지운다
        File(context.filesDir, BACKUP_NAME).delete()
        val uri = findOwnFile(context) ?: return ""
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8).removePrefix("﻿")
            } ?: ""
        } catch (e: Exception) {
            ""
        }
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

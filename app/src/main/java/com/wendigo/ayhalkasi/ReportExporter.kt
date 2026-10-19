package com.wendigo.ayhalkasi

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.LocalDate

object ReportExporter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 42f

    private val INK = Color.rgb(0x3A, 0x2E, 0x3F)
    private val BRAND = Color.rgb(0x7A, 0x2E, 0x66)
    private val DIM = Color.rgb(0x8A, 0x7C, 0x90)

    fun writePdf(context: Context, out: OutputStream) {
        Store.init(context)
        val r = CycleEngine.analyze()
        val today = LocalDate.now()

        val title = paint(20f, BRAND, true)
        val heading = paint(13f, BRAND, true)
        val body = paint(11f, INK, false)
        val small = paint(9f, DIM, false)

        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        var canvas = page.canvas
        var y = MARGIN

        fun ensure(h: Float) {
            if (y + h > PAGE_H - MARGIN) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                canvas = page.canvas
                y = MARGIN
            }
        }

        fun line(text: String, p: Paint, gap: Float = 5f) {
            for (l in wrap(text, p, PAGE_W - 2 * MARGIN)) {
                ensure(p.textSize + gap)
                canvas.drawText(l, MARGIN, y + p.textSize, p)
                y += p.textSize + gap
            }
        }

        fun spacer(h: Float) {
            ensure(h)
            y += h
        }

        line(context.getString(R.string.app_name) + " — Döngü Raporu", title, 8f)
        line("Oluşturma: ${Dates.long(today)}", small, 12f)

        line("Özet", heading, 4f)
        line("Kayıtlı adet: ${r.starts.size}", body)
        line("Ortalama döngü: ${r.avgCycle} gün", body)
        line("Ortalama adet süresi: ${r.avgPeriod} gün", body)
        line("Tahmin güveni: ${confLabel(context, r.confidence)}", body)
        if (r.irregular) line("Düzensiz döngü olarak değerlendirildi.", body)
        if (r.lastStart != null) line("Son adet: ${Dates.long(r.lastStart)}", body)
        if (r.nextStart != null) line("Sonraki adet tahmini: ${r.nextRangeText()}", body)
        if (r.ovulation != null) line("Yumurtlama tahmini: ${Dates.long(r.ovulation)}", body)
        if (r.fertileStart != null && r.fertileEnd != null) {
            line("Doğurgan pencere: ${Dates.long(r.fertileStart)} – ${Dates.long(r.fertileEnd)}", body)
        }
        spacer(8f)

        line("Adet Geçmişi", heading, 4f)
        val groups = CycleEngine.groupConsecutive(Store.periodDates())
        if (groups.isEmpty()) {
            line("Kayıt yok.", body)
        } else {
            for (g in groups) {
                line("${Dates.long(g.first())} – ${Dates.long(g.last())}  (${g.size} gün)", body)
            }
        }
        spacer(8f)

        line("En Sık Belirtiler", heading, 4f)
        val names = context.resources.getStringArray(R.array.symptom_names)
        val top = Insights.topSymptoms(8)
        if (top.isEmpty()) {
            line("Belirti kaydı yok.", body)
        } else {
            for ((idx, count) in top) line("${names.getOrElse(idx) { "" }}: $count", body)
        }
        spacer(8f)

        val notes = Store.allEntries().filter { it.second.note.isNotBlank() }
        if (notes.isNotEmpty()) {
            line("Notlar", heading, 4f)
            for ((d, log) in notes) line("${Dates.long(d)}: ${log.note}", body)
            spacer(8f)
        }

        val medDates = Store.allMedTaken()
        if (medDates.isNotEmpty()) {
            line("İlaç / Doğum kontrolü", heading, 4f)
            line("Alınan gün sayısı: ${medDates.size}", body)
            spacer(8f)
        }

        spacer(6f)
        line(
            "Bu rapor geçmiş kayıtlara dayanır, kesin değildir ve tıbbi tavsiye yerine geçmez. " +
                "Yalnızca bilgilendirme amaçlıdır.",
            small
        )

        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    fun writeCsv(context: Context, out: OutputStream) {
        Store.init(context)
        val names = context.resources.getStringArray(R.array.symptom_names)
        OutputStreamWriter(out, Charsets.UTF_8).use { w ->
            w.write("tarih;akis;agri;ruh_hali;belirtiler;ilac;not\n")
            val medSet = Store.allMedTaken().map { it.toString() }.toHashSet()
            for ((d, log) in Store.allEntries()) {
                val syms = log.symptoms.sorted().mapNotNull { names.getOrNull(it) }.joinToString("|")
                val med = if (medSet.contains(d.toString())) "1" else "0"
                w.write(
                    listOf(
                        d.toString(),
                        log.flow.toString(),
                        log.pain.toString(),
                        log.mood.toString(),
                        syms,
                        med,
                        csv(log.note)
                    ).joinToString(";")
                )
                w.write("\n")
            }
            w.flush()
        }
    }

    private fun csv(s: String): String {
        if (s.isEmpty()) return ""
        return "\"" + s.replace("\"", "\"\"") + "\""
    }

    private fun confLabel(context: Context, c: CycleEngine.Confidence): String = context.getString(
        when (c) {
            CycleEngine.Confidence.HIGH -> R.string.conf_high
            CycleEngine.Confidence.MEDIUM -> R.string.conf_medium
            CycleEngine.Confidence.LOW -> R.string.conf_low
        }
    )

    private fun paint(size: Float, color: Int, bold: Boolean): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private fun wrap(text: String, p: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val words = text.split(' ')
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (word in words) {
            val candidate = if (sb.isEmpty()) word else "$sb $word"
            if (p.measureText(candidate) <= maxWidth || sb.isEmpty()) {
                sb.setLength(0)
                sb.append(candidate)
            } else {
                out.add(sb.toString())
                sb.setLength(0)
                sb.append(word)
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }
}

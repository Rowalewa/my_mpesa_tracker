package com.example.my_mpesa_tracker.ui.dashboard

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import com.example.my_mpesa_tracker.util.SpendingStats
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── PDF report ────────────────────────────────────────────────────────────────
// Print-friendly (white pages, brand-green accents) rather than a dark screenshot.
// Emojis are stripped: PdfDocument's canvas doesn't reliably render colour-emoji
// fonts, and a row of empty boxes in a financial document looks broken.

private val GREEN = 0xFF00A550.toInt()
private val GREEN_DARK = 0xFF007A3C.toInt()
private val GREEN_SOFT = 0xFFA6DBC0.toInt()
private val RED_DEEP = 0xFF8B0000.toInt()
private val INK = 0xFF1A1A2E.toInt()
private val BODY = 0xFF444B5C.toInt()
private val MUTED = 0xFF8A93A6.toInt()
private val RULE = 0xFFE3E7EF.toInt()
private val TILE = 0xFFF3F5F9.toInt()
private val NEG = 0xFFD64545.toInt()
private val POS = 0xFF1E8E4E.toInt()
private val WHITE = 0xFFFFFFFF.toInt()

private const val PAGE_W = 595
private const val PAGE_H = 842
private const val MARGIN = 36f
private const val CONTENT_W = PAGE_W - 2 * MARGIN
private const val BOTTOM = PAGE_H - 52f
private const val MAX_INFLOW_ROWS = 14

/** Removes emoji and pictographic symbols; keeps ordinary punctuation like – — •. */
fun String.stripEmoji(): String {
    val sb = StringBuilder(length)
    var i = 0
    while (i < length) {
        val cp = codePointAt(i)
        val isPictographic = cp >= 0x1F000 ||
                cp in 0x2600..0x27BF ||
                cp in 0x2190..0x21FF ||
                cp in 0x2B00..0x2BFF ||
                cp == 0xFE0F || cp == 0x200D
        if (!isPictographic) sb.appendCodePoint(cp)
        i += Character.charCount(cp)
    }
    return sb.toString().replace(Regex(" {2,}"), " ").trim()
}

object PdfReportExporter {

    fun export(
        context: Context,
        report: SpendingReport,
        stats: SpendingStats,
        charts: ReportChartData,
        totalCosts: Double
    ): File? {
        return try {
            val doc = PdfDocument()
            val writer = PdfWriter(doc, report.periodLabel.stripEmoji())
            writer.render(report, stats, charts, totalCosts)
            writer.close()

            val safeName = report.periodLabel.stripEmoji().replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val file = File(dir, "Pesalyzer_Report_$safeName.pdf")
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()
            file
        } catch (_: Exception) {
            null
        }
    }

    fun share(context: Context, file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Pesalyzer Spending Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share report via"))
    }
}

private class PdfWriter(private val doc: PdfDocument, private val footerLabel: String) {

    private var pageNo = 0
    private var page: PdfDocument.Page? = null
    private lateinit var canvas: Canvas
    private var y = MARGIN

    private val regular: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    private val bold: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    // ── page management ──

    private fun startNewPage() {
        closePage()
        pageNo++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        page = p
        canvas = p.canvas
        y = MARGIN
    }

    private fun closePage() {
        page?.let {
            drawFooter()
            doc.finishPage(it)
        }
        page = null
    }

    fun close() = closePage()

    private fun ensure(height: Float) {
        if (page == null || y + height > BOTTOM) startNewPage()
    }

    private fun drawFooter() {
        val line = Paint().apply { color = RULE; strokeWidth = 0.8f }
        canvas.drawLine(MARGIN, PAGE_H - 34f, MARGIN + CONTENT_W, PAGE_H - 34f, line)
        text("Pesalyzer  •  Spending Report  •  $footerLabel", MARGIN, PAGE_H - 20f, 8f, MUTED)
        text("Page $pageNo", MARGIN + CONTENT_W, PAGE_H - 20f, 8f, MUTED, align = Paint.Align.RIGHT)
    }

    // ── drawing primitives ──

    private fun paint(size: Float, color: Int, isBold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (isBold) bold else regular
    }

    private fun text(
        value: String, x: Float, baseline: Float, size: Float, color: Int,
        isBold: Boolean = false, align: Paint.Align = Paint.Align.LEFT
    ) {
        val p = paint(size, color, isBold).apply { textAlign = align }
        canvas.drawText(value, x, baseline, p)
    }

    private fun fitSize(value: String, maxWidth: Float, start: Float, isBold: Boolean): Float {
        var size = start
        val p = paint(size, 0, isBold)
        while (size > 8f) {
            p.textSize = size
            if (p.measureText(value) <= maxWidth) break
            size -= 0.5f
        }
        return size
    }

    private fun ellipsize(value: String, size: Float, maxWidth: Float, isBold: Boolean = false): String =
        TextUtils.ellipsize(value, paint(size, 0, isBold), maxWidth, TextUtils.TruncateAt.END).toString()

    private fun roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) {
        canvas.drawRoundRect(RectF(l, t, r, b), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    private fun layoutFor(value: String, size: Float, color: Int, width: Float, isBold: Boolean = false): StaticLayout =
        StaticLayout.Builder.obtain(value, 0, value.length, paint(size, color, isBold), width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.18f)
            .setIncludePad(false)
            .build()

    private fun paragraph(value: String, size: Float, color: Int, after: Float = 0f) {
        if (value.isBlank()) return
        val layout = layoutFor(value, size, color, CONTENT_W)
        ensure(layout.height.toFloat())
        canvas.save(); canvas.translate(MARGIN, y); layout.draw(canvas); canvas.restore()
        y += layout.height + after
    }

    private fun bulletRow(value: String, dot: Int) {
        if (value.isBlank()) return
        val layout = layoutFor(value, 10f, BODY, CONTENT_W - 16f)
        ensure(layout.height.toFloat() + 6f)
        canvas.drawCircle(MARGIN + 4f, y + 6f, 3.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dot })
        canvas.save(); canvas.translate(MARGIN + 16f, y); layout.draw(canvas); canvas.restore()
        y += layout.height + 6f
    }

    private fun sectionTitle(title: String) {
        ensure(60f)
        y += 8f
        roundRect(MARGIN, y, MARGIN + 4f, y + 15f, 2f, GREEN)
        text(title, MARGIN + 12f, y + 12.5f, 13f, INK, isBold = true)
        y += 26f
    }

    // ── report ──

    fun render(report: SpendingReport, stats: SpendingStats, charts: ReportChartData, totalCosts: Double) {
        startNewPage()
        drawHeaderBand(report.periodLabel.stripEmoji())
        drawHero(stats)

        sectionTitle("Summary")
        paragraph(report.summary.stripEmoji(), 10.5f, BODY, after = 6f)

        if (report.alerts.isNotEmpty()) {
            sectionTitle("Alerts")
            report.alerts.forEach { bulletRow(it.stripEmoji(), NEG) }
        }
        if (report.highlights.isNotEmpty()) {
            sectionTitle("Highlights")
            report.highlights.forEach { bulletRow(it.stripEmoji(), GREEN) }
        }
        if (report.patterns.isNotEmpty()) {
            sectionTitle("Spending patterns")
            report.patterns.forEach { bulletRow(it.stripEmoji(), MUTED) }
        }

        drawDonut(charts)
        drawRecipients(charts)
        drawWeekday(charts)

        val sliceColors = charts.slices.associate { it.label to it.color.toArgb() }
        val spendingRows = report.tableRows + listOfNotNull(charts.feeRow)
        drawTable(
            title = "Breakdown by category",
            firstHeader = "Category",
            rows = spendingRows,
            amountColor = NEG,
            dotFor = { sliceColors[it] ?: ReportCharts.otherColor.toArgb() },
            totalLabel = "Total spent",
            totalValue = formatKsh(stats.totalSpent)
        )

        if (report.inflowRows.isNotEmpty()) {
            val shown = report.inflowRows.take(MAX_INFLOW_ROWS)
            val rest = report.inflowRows.drop(MAX_INFLOW_ROWS)
            val rows = if (rest.isEmpty()) shown else shown + TableRow(
                "Other senders (${rest.size})",
                rest.sumOf { it.amount },
                rest.sumOf { it.count },
                rest.sumOf { it.percentage }
            )
            drawTable(
                title = "Inflow by sender",
                firstHeader = "Sender / source",
                rows = rows,
                amountColor = POS,
                dotFor = null,
                totalLabel = "Total received",
                totalValue = formatKsh(stats.totalReceived)
            )
        }

        drawStats(stats, totalCosts)

        y += 10f
        paragraph(
            "Figures are derived from M-Pesa SMS records stored on this device and may differ slightly " +
                    "from your official M-Pesa statement. Check the statement for anything important.",
            8.5f, MUTED
        )
        paragraph("Generated by Pesalyzer on ${SimpleDateFormat("d MMM yyyy, h:mm a", Locale.US).format(Date())}", 8.5f, MUTED)
    }

    private fun drawHeaderBand(periodLabel: String) {
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 96f, Paint().apply { color = GREEN })
        text("Pesalyzer", MARGIN, 46f, 24f, WHITE, isBold = true)
        text("Spending Report", MARGIN, 66f, 12f, WHITE)
        text(periodLabel, MARGIN + CONTENT_W, 46f, 13f, WHITE, isBold = true, align = Paint.Align.RIGHT)
        text(
            SimpleDateFormat("d MMM yyyy", Locale.US).format(Date()),
            MARGIN + CONTENT_W, 66f, 10f, WHITE, align = Paint.Align.RIGHT
        )
        y = 118f
    }

    private fun drawHero(stats: SpendingStats) {
        val h = 96f
        ensure(h)
        val positive = stats.netFlow >= 0
        roundRect(MARGIN, y, MARGIN + CONTENT_W, y + h, 14f, if (positive) GREEN_DARK else RED_DEEP)

        text("Net cash flow", MARGIN + 18f, y + 26f, 10.5f, WHITE)
        val big = formatKsh(stats.netFlow)
        val bigSize = fitSize(big, 240f, 26f, true)
        text(big, MARGIN + 18f, y + 62f, bigSize, WHITE, isBold = true)

        val inX = MARGIN + 300f
        val outX = MARGIN + 412f
        text("Money in", inX, y + 30f, 9.5f, WHITE)
        text(formatKsh(stats.totalReceived), inX, y + 50f, fitSize(formatKsh(stats.totalReceived), 100f, 13f, true), 0xFFB8F0CB.toInt(), isBold = true)
        text("Money out", outX, y + 30f, 9.5f, WHITE)
        text(formatKsh(stats.totalSpent), outX, y + 50f, fitSize(formatKsh(stats.totalSpent), 100f, 13f, true), 0xFFFFC9C9.toInt(), isBold = true)
        y += h + 6f
    }

    private fun drawDonut(charts: ReportChartData) {
        if (charts.slices.isEmpty()) return
        val blockH = 150f
        ensure(blockH + 40f)
        sectionTitle("Where the money went")

        val cx = MARGIN + 68f
        val cy = y + 68f
        val r = 54f
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 22f
            strokeCap = Paint.Cap.BUTT
        }
        var start = -90f
        charts.slices.forEach { slice ->
            arc.color = slice.color.toArgb()
            val sweep = slice.fraction * 360f
            canvas.drawArc(oval, start, maxOf(0.5f, sweep - 1.2f), false, arc)
            start += sweep
        }
        text(compactAmount(charts.totalOutflow), cx, cy + 3f, 12f, INK, isBold = true, align = Paint.Align.CENTER)
        text("spent", cx, cy + 16f, 8.5f, MUTED, align = Paint.Align.CENTER)

        val legendX = MARGIN + 150f
        val right = MARGIN + CONTENT_W
        val amountRight = right - 58f
        val labelMax = amountRight - 84f - (legendX + 15f)
        var ly = y + 14f
        charts.slices.forEach { slice ->
            roundRect(legendX, ly - 8f, legendX + 9f, ly + 1f, 2f, slice.color.toArgb())
            text(ellipsize(slice.label.stripEmoji(), 10f, labelMax), legendX + 15f, ly, 10f, BODY)
            text(formatKsh(slice.value), amountRight, ly, 9f, MUTED, align = Paint.Align.RIGHT)
            text(formatShare(slice.fraction), right, ly, 10f, INK, isBold = true, align = Paint.Align.RIGHT)
            ly += 20f
        }
        y += blockH + 6f
    }

    private fun drawRecipients(charts: ReportChartData) {
        val max = charts.topRecipients.maxOfOrNull { it.second } ?: return
        if (max <= 0.0) return
        ensure(40f + 30f * charts.topRecipients.size)
        sectionTitle("Top recipients")

        val barLeft = MARGIN + 24f
        val barW = CONTENT_W - 24f
        charts.topRecipients.forEachIndexed { i, (name, amount) ->
            roundRect(MARGIN, y, MARGIN + 17f, y + 17f, 5f, 0xFFDDF2E7.toInt())
            text("${i + 1}", MARGIN + 8.5f, y + 12.5f, 9f, GREEN_DARK, isBold = true, align = Paint.Align.CENTER)
            text(ellipsize(name.stripEmoji(), 10.5f, barW - 110f), barLeft, y + 12.5f, 10.5f, INK)
            text(formatKsh(amount), MARGIN + CONTENT_W, y + 12.5f, 10.5f, INK, isBold = true, align = Paint.Align.RIGHT)
            roundRect(barLeft, y + 21f, barLeft + barW, y + 25f, 2f, RULE)
            roundRect(barLeft, y + 21f, barLeft + barW * (amount / max).toFloat().coerceIn(0.02f, 1f), y + 25f, 2f, GREEN)
            y += 30f
        }
    }

    private fun drawWeekday(charts: ReportChartData) {
        val values = charts.weekdaySpend
        val max = values.maxOrNull() ?: return
        if (max <= 0.0) return
        val peak = values.indexOf(max)
        ensure(170f)
        sectionTitle("Spending by day of week")

        val slot = CONTENT_W / 7f
        val baseline = y + 84f
        values.forEachIndexed { i, v ->
            val cx = MARGIN + slot * i + slot / 2f
            val barH = maxOf(2f, (64.0 * (v / max)).toFloat())
            roundRect(cx - 17f, baseline - barH, cx + 17f, baseline, 5f, if (i == peak) GREEN else GREEN_SOFT)
            if (v > 0) text(compactAmount(v), cx, baseline - barH - 4f, 8.5f, MUTED, align = Paint.Align.CENTER)
            text(ReportCharts.weekdayShort[i], cx, baseline + 14f, 9f, if (i == peak) INK else MUTED, isBold = i == peak, align = Paint.Align.CENTER)
        }
        y += 108f
        paragraph("Highest on ${ReportCharts.weekdayFull[peak]}s — ${formatKsh(max)} across the period.", 9.5f, BODY, after = 4f)
    }

    private fun drawTable(
        title: String,
        firstHeader: String,
        rows: List<TableRow>,
        amountColor: Int,
        dotFor: ((String) -> Int)?,
        totalLabel: String,
        totalValue: String
    ) {
        if (rows.isEmpty()) return
        val right = MARGIN + CONTENT_W
        val shareRight = right - 8f
        val amountRight = shareRight - 62f
        val txRight = amountRight - 92f
        val nameLeft = MARGIN + 10f + if (dotFor != null) 14f else 0f
        val nameMax = txRight - 30f - nameLeft

        fun header() {
            roundRect(MARGIN, y, right, y + 22f, 6f, TILE)
            text(firstHeader.uppercase(), MARGIN + 10f, y + 14.5f, 8f, MUTED, isBold = true)
            text("TX", txRight, y + 14.5f, 8f, MUTED, isBold = true, align = Paint.Align.RIGHT)
            text("AMOUNT", amountRight, y + 14.5f, 8f, MUTED, isBold = true, align = Paint.Align.RIGHT)
            text("SHARE", shareRight, y + 14.5f, 8f, MUTED, isBold = true, align = Paint.Align.RIGHT)
            y += 26f
        }

        ensure(90f)
        sectionTitle(title)
        header()

        val rule = Paint().apply { color = RULE; strokeWidth = 0.6f }
        rows.forEach { row ->
            if (y + 22f > BOTTOM) {
                startNewPage()
                header()
            }
            if (dotFor != null) {
                canvas.drawCircle(MARGIN + 14f, y + 8.5f, 3.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotFor(row.category) })
            }
            text(ellipsize(row.category.stripEmoji(), 10f, nameMax), nameLeft, y + 12f, 10f, INK)
            text("${row.count}", txRight, y + 12f, 10f, MUTED, align = Paint.Align.RIGHT)
            text(formatKsh(row.amount), amountRight, y + 12f, 10f, amountColor, isBold = true, align = Paint.Align.RIGHT)
            text(String.format(Locale.US, "%.1f%%", row.percentage), shareRight, y + 12f, 10f, MUTED, align = Paint.Align.RIGHT)
            canvas.drawLine(MARGIN, y + 19f, right, y + 19f, rule)
            y += 21f
        }

        ensure(26f)
        y += 4f
        text(totalLabel, MARGIN + 10f, y + 13f, 10.5f, INK, isBold = true)
        text(totalValue, shareRight, y + 13f, 10.5f, amountColor, isBold = true, align = Paint.Align.RIGHT)
        y += 24f
    }

    private fun drawStats(stats: SpendingStats, totalCosts: Double) {
        val cells = listOf(
            Triple("TOTAL SPENT", formatKsh(stats.totalSpent), NEG),
            Triple("TOTAL RECEIVED", formatKsh(stats.totalReceived), POS),
            Triple("LARGEST TRANSACTION", formatKsh(stats.maxTransaction), INK),
            Triple("SMALLEST TRANSACTION", formatKsh(stats.minTransaction), INK),
            Triple("AVERAGE TRANSACTION", formatKsh(stats.avgTransaction), INK),
            Triple("TRANSACTIONS", "${stats.transactionCount}", INK),
            Triple("TRANSACTION FEES", formatKsh(totalCosts), NEG)
        )
        val rowsNeeded = (cells.size + 1) / 2
        ensure(40f + rowsNeeded * 48f)
        sectionTitle("Statistics")

        val gap = 10f
        val cellW = (CONTENT_W - gap) / 2f
        cells.chunked(2).forEach { pair ->
            pair.forEachIndexed { col, (label, value, color) ->
                val x = MARGIN + col * (cellW + gap)
                roundRect(x, y, x + cellW, y + 40f, 8f, TILE)
                text(label, x + 12f, y + 15f, 7.5f, MUTED, isBold = true)
                text(value, x + 12f, y + 32f, fitSize(value, cellW - 24f, 13f, true), color, isBold = true)
            }
            y += 48f
        }
    }
}

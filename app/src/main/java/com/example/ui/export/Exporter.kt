package com.example.ui.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.ui.SkuDetailSummary
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

object Exporter {

    // Helper to format currency
    private fun formatRupiah(amount: Double): String {
        val symbols = DecimalFormatSymbols(Locale("in", "ID"))
        symbols.currencySymbol = "Rp "
        symbols.groupingSeparator = '.'
        symbols.decimalSeparator = ','
        val formatter = DecimalFormat("Rp #,###.##", symbols)
        return formatter.format(amount).replace(",00", "")
    }

    private fun formatNumber(num: Number): String {
        return DecimalFormat("#,###", DecimalFormatSymbols(Locale("in", "ID"))).format(num)
    }

    // Modern scoped storage saving
    private fun saveFileToDownloads(
        context: Context,
        filename: String,
        mimeType: String,
        writeBlock: (OutputStream) -> Unit
    ): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/SalesExplorer")
            }
        }

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            // MediaStore.Downloads.EXTERNAL_CONTENT_URI might not be available on API < 29
            // But we can fallback to External Content URI for Files
            resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
        }

        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    writeBlock(outputStream)
                }
                return uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
            }
        }
        return null
    }

    // Export to Excel file
    fun exportToExcel(context: Context, summary: SkuDetailSummary): Uri? {
        val workbook = XSSFWorkbook()

        // Page 1: Product Summary
        val summarySheet = workbook.createSheet("Detail SKU")
        
        // Header styling
        val fontHeader = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 14.toShort()
        }
        val cellStyleHeader = workbook.createCellStyle().apply {
            setFont(fontHeader)
        }

        val row0 = summarySheet.createRow(0)
        val hCell = row0.createCell(0)
        hCell.setCellValue("Sales Data Summary Report")
        hCell.setCellStyle(cellStyleHeader)

        summarySheet.createRow(2).apply {
            createCell(0).setCellValue("SKU ID")
            createCell(1).setCellValue(summary.sku)
        }
        summarySheet.createRow(3).apply {
            createCell(0).setCellValue("Item Description")
            createCell(1).setCellValue(summary.itemDescription)
        }
        summarySheet.createRow(4).apply {
            createCell(0).setCellValue("Department")
            createCell(1).setCellValue(summary.dept)
        }
        summarySheet.createRow(5).apply {
            createCell(0).setCellValue("Total Sales Qty")
            createCell(1).setCellValue(summary.totalQty.toDouble())
        }
        summarySheet.createRow(6).apply {
            createCell(0).setCellValue("Total Sales Amount")
            createCell(1).setCellValue(summary.totalAmount)
        }
        summarySheet.createRow(7).apply {
            createCell(0).setCellValue("Avg Daily Qty")
            createCell(1).setCellValue(summary.avgDailyQty)
        }
        summarySheet.createRow(8).apply {
            createCell(0).setCellValue("Avg Daily Amount")
            createCell(1).setCellValue(summary.avgDailyAmount)
        }
        summarySheet.createRow(9).apply {
            createCell(0).setCellValue("Highest Day")
            createCell(1).setCellValue("${summary.highestDayDate} (${formatRupiah(summary.highestDayAmount)})")
        }
        summarySheet.createRow(10).apply {
            createCell(0).setCellValue("Lowest Day")
            createCell(1).setCellValue("${summary.lowestDayDate} (${formatRupiah(summary.lowestDayAmount)})")
        }

        summarySheet.autoSizeColumn(0)
        summarySheet.autoSizeColumn(1)

        // Page 2: Sales By Date
        val dateSheet = workbook.createSheet("Sales By Date")
        val dateHeaderRow = dateSheet.createRow(0)
        dateHeaderRow.createCell(0).setCellValue("Tanggal")
        dateHeaderRow.createCell(1).setCellValue("Qty")
        dateHeaderRow.createCell(2).setCellValue("Sales Amount")

        summary.dailySales.forEachIndexed { idx, item ->
            val r = dateSheet.createRow(idx + 1)
            r.createCell(0).setCellValue(item.date)
            r.createCell(1).setCellValue(item.qty.toDouble())
            r.createCell(2).setCellValue(item.amount)
        }
        dateSheet.autoSizeColumn(0)
        dateSheet.autoSizeColumn(1)
        dateSheet.autoSizeColumn(2)

        // Page 3: Sales By Month
        val monthSheet = workbook.createSheet("Sales By Month")
        val monthHeaderRow = monthSheet.createRow(0)
        monthHeaderRow.createCell(0).setCellValue("Bulan")
        monthHeaderRow.createCell(1).setCellValue("Total Qty")
        monthHeaderRow.createCell(2).setCellValue("Total Sales Amount")

        summary.monthlySales.forEachIndexed { idx, item ->
            val r = monthSheet.createRow(idx + 1)
            r.createCell(0).setCellValue(item.monthLabel)
            r.createCell(1).setCellValue(item.qty.toDouble())
            r.createCell(2).setCellValue(item.amount)
        }
        monthSheet.autoSizeColumn(0)
        monthSheet.autoSizeColumn(1)
        monthSheet.autoSizeColumn(2)

        val format = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val filename = "Sales_Report_${summary.sku}_${format.format(Date())}.xlsx"
        
        val uri = saveFileToDownloads(
            context,
            filename,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ) { out ->
            workbook.write(out)
        }
        workbook.close()
        return uri
    }

    // Export to structured PDF with dynamic stats, grids, tables, and vector graphs!
    fun exportToPdf(context: Context, summary: SkuDetailSummary): Uri? {
        val pdfDocument = PdfDocument()

        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 12f
        }

        val boldPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 12f
            strokeWidth = 1.5f
            style = Paint.Style.FILL_AND_STROKE
        }

        val primaryPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#1E88E5") // Material Blue
            style = Paint.Style.FILL
        }

        val gridPaint = Paint().apply {
            color = Color.parseColor("#ECEFF1") // Light grey
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        // ================= PAGE 1 =================
        // Page specification: A4 standard dimensions at 72dpi = 595 x 842 points
        val pageInfo1 = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page1 = pdfDocument.startPage(pageInfo1)
        val canvas1 = page1.canvas

        // Header Background block
        canvas1.drawRect(RectF(30f, 30f, 565f, 90f), primaryPaint)

        // Header Title Text
        textPaint.color = Color.WHITE
        textPaint.textSize = 20f
        boldPaint.color = Color.WHITE
        boldPaint.textSize = 20f
        canvas1.drawText("SALES DATA ANALYSIS REPORT", 45f, 68f, boldPaint)

        // Reset Paint
        textPaint.color = Color.BLACK
        boldPaint.color = Color.BLACK

        // Details Section Title
        boldPaint.textSize = 14f
        canvas1.drawText("Rincian SKU", 30f, 120f, boldPaint)
        canvas1.drawLine(30f, 128f, 565f, 128f, gridPaint)

        // Meta List
        textPaint.textSize = 11f
        var yPos = 150f
        val drawMetaRow = { label: String, valStr: String ->
            boldPaint.textSize = 11f
            canvas1.drawText(label, 40f, yPos, boldPaint)
            textPaint.color = Color.DKGRAY
            canvas1.drawText(valStr, 180f, yPos, textPaint)
            yPos += 20f
        }

        drawMetaRow("SKU ID", summary.sku)
        drawMetaRow("Nama Produk", summary.itemDescription)
        drawMetaRow("Department", summary.dept)
        drawMetaRow("Total Kuantitas Terjual", formatNumber(summary.totalQty))
        drawMetaRow("Total Nilai Penjualan", formatRupiah(summary.totalAmount))
        drawMetaRow("Rata-rata Harian Qty", String.format(Locale.US, "%.1f", summary.avgDailyQty))
        drawMetaRow("Rata-rata Harian Nilai", formatRupiah(summary.avgDailyAmount))
        drawMetaRow("Hari Penjualan Tertinggi", "${summary.highestDayDate} (${formatRupiah(summary.highestDayAmount)})")
        drawMetaRow("Hari Penjualan Terendah", "${summary.lowestDayDate} (${formatRupiah(summary.lowestDayAmount)})")

        // Draw sales by Date table (Top 10 entries)
        yPos += 15f
        boldPaint.textSize = 14f
        boldPaint.color = Color.BLACK
        canvas1.drawText("Tabel Penjualan Harian (Terbaru)", 30f, yPos, boldPaint)
        canvas1.drawLine(30f, yPos + 8f, 565f, yPos + 8f, gridPaint)
        yPos += 25f

        // Table Header
        boldPaint.textSize = 10f
        canvas1.drawText("Tanggal", 40f, yPos, boldPaint)
        canvas1.drawText("Kuantitas (Qty)", 220f, yPos, boldPaint)
        canvas1.drawText("Nilai Penjualan", 400f, yPos, boldPaint)
        canvas1.drawLine(30f, yPos + 5f, 565f, yPos + 5f, gridPaint)
        yPos += 20f

        textPaint.textSize = 10f
        val listToDraw = summary.dailySales.take(15) // Limit page size to prevent overflow
        listToDraw.forEach { item ->
            val formattedDate = try {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(item.date)
                SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(date!!)
            } catch (e: Exception) {
                item.date
            }
            textPaint.color = Color.BLACK
            canvas1.drawText(formattedDate, 40f, yPos, textPaint)
            canvas1.drawText(formatNumber(item.qty), 220f, yPos, textPaint)
            canvas1.drawText(formatRupiah(item.amount), 400f, yPos, textPaint)
            
            canvas1.drawLine(30f, yPos + 4f, 565f, yPos + 4f, gridPaint)
            yPos += 18f
        }

        if (summary.dailySales.size > 15) {
            textPaint.color = Color.GRAY
            canvas1.drawText("Menampilkan 15 dari ${summary.dailySales.size} baris data.", 40f, yPos + 5f, textPaint)
        }

        // Draw Footer
        textPaint.color = Color.GRAY
        textPaint.textSize = 9f
        canvas1.drawText("Sales Data Explorer - Halaman 1 dari 2", 230f, 825f, textPaint)

        pdfDocument.finishPage(page1)

        // ================= PAGE 2 =================
        val pageInfo2 = PdfDocument.PageInfo.Builder(595, 842, 2).create()
        val page2 = pdfDocument.startPage(pageInfo2)
        val canvas2 = page2.canvas

        // Title Page 2
        boldPaint.textSize = 14f
        boldPaint.color = Color.BLACK
        canvas2.drawText("Akumulasi Penjualan Bulanan", 30f, 50f, boldPaint)
        canvas2.drawLine(30f, 58f, 565f, 58f, gridPaint)

        yPos = 80f
        // Table Header Monthly
        boldPaint.textSize = 10f
        canvas2.drawText("Bulan", 40f, yPos, boldPaint)
        canvas2.drawText("Total Kuantitas (Qty)", 220f, yPos, boldPaint)
        canvas2.drawText("Total Nilai Penjualan", 400f, yPos, boldPaint)
        canvas2.drawLine(30f, yPos + 5f, 565f, yPos + 5f, gridPaint)
        yPos += 20f

        textPaint.textSize = 10f
        summary.monthlySales.forEach { item ->
            textPaint.color = Color.BLACK
            canvas2.drawText(item.monthLabel, 40f, yPos, textPaint)
            canvas2.drawText(formatNumber(item.qty), 220f, yPos, textPaint)
            canvas2.drawText(formatRupiah(item.amount), 400f, yPos, textPaint)
            canvas2.drawLine(30f, yPos + 4f, 565f, yPos + 4f, gridPaint)
            yPos += 18f
        }

        // DRAW GRAPHIC TREND SECTION
        yPos += 30f
        boldPaint.textSize = 14f
        canvas2.drawText("Grafik Tren Penjualan", 30f, yPos, boldPaint)
        canvas2.drawLine(30f, yPos + 8f, 565f, yPos + 8f, gridPaint)
        
        yPos += 30f
        // We'll draw a beautiful combined Line chart for dates.
        // Let's draw trend line canvas.
        val graphLeft = 60f
        val graphTop = yPos
        val graphWidth = 460f
        val graphHeight = 150f
        val graphBottom = graphTop + graphHeight

        // Draw trend container axis
        val axisPaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        val gridLinePaint = Paint().apply {
            color = Color.parseColor("#EEEEEE")
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        // Draw graph border and y-axis grids
        canvas2.drawRect(RectF(graphLeft, graphTop, graphLeft + graphWidth, graphBottom), axisPaint)
        for (i in 1..4) {
            val hY = graphBottom - (graphHeight / 5) * i
            canvas2.drawLine(graphLeft, hY, graphLeft + graphWidth, hY, gridLinePaint)
        }

        // Let's chart the Daily sales (limit to 10 points for visibility)
        val chartPoints = summary.dailySales.sortedBy { it.date }.takeLast(10)
        
        if (chartPoints.isNotEmpty()) {
            val maxQty = chartPoints.maxOf { it.qty }.coerceAtLeast(1)
            val maxAmount = chartPoints.maxOf { it.amount }.coerceAtLeast(1.0)

            val stepX = graphWidth / (chartPoints.size - 1).coerceAtLeast(1)

            val blueLinePaint = Paint().apply {
                color = Color.parseColor("#1E88E5")
                strokeWidth = 2.5f
                style = Paint.Style.STROKE
                isAntiAlias = true
            }

            val orangeLinePaint = Paint().apply {
                color = Color.parseColor("#FF9800")
                strokeWidth = 2.5f
                style = Paint.Style.STROKE
                isAntiAlias = true
            }

            // Draw lines
            for (i in 0 until chartPoints.size - 1) {
                val p1 = chartPoints[i]
                val p2 = chartPoints[i + 1]

                val x1 = graphLeft + stepX * i
                val x2 = graphLeft + stepX * (i + 1)

                // Normalized Y points for Qty
                val yQty1 = (graphBottom - (p1.qty.toDouble() / maxQty) * graphHeight).toFloat()
                val yQty2 = (graphBottom - (p2.qty.toDouble() / maxQty) * graphHeight).toFloat()

                canvas2.drawLine(x1, yQty1, x2, yQty2, blueLinePaint)

                // Normalized Y points for Amount
                val yAmt1 = (graphBottom - (p1.amount / maxAmount) * graphHeight).toFloat()
                val yAmt2 = (graphBottom - (p2.amount / maxAmount) * graphHeight).toFloat()

                canvas2.drawLine(x1, yAmt1, x2, yAmt2, orangeLinePaint)
            }

            // Draw labels
            textPaint.textSize = 8f
            textPaint.color = Color.BLACK
            
            // Legends
            val legendPaint = Paint().apply {
                style = Paint.Style.FILL
            }

            legendPaint.color = Color.parseColor("#1E88E5")
            canvas2.drawRect(RectF(80f, graphBottom + 15f, 95f, graphBottom + 25f), legendPaint)
            canvas2.drawText("Sales Qty Trend", 105f, graphBottom + 23f, textPaint)

            legendPaint.color = Color.parseColor("#FF9800")
            canvas2.drawRect(RectF(250f, graphBottom + 15f, 265f, graphBottom + 25f), legendPaint)
            canvas2.drawText("Sales Amount Trend (${formatRupiah(maxAmount)} max)", 275f, graphBottom + 23f, textPaint)

            // Draw Date ticks on X Axis
            chartPoints.forEachIndexed { i, p ->
                val x = graphLeft + stepX * i
                val label = try {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(p.date)
                    SimpleDateFormat("dd/MM", Locale.US).format(date!!)
                } catch (e: Exception) {
                    p.date.takeLast(5)
                }
                
                canvas2.drawLine(x, graphBottom, x, graphBottom + 3f, axisPaint)
                canvas2.drawText(label, x - 10f, graphBottom + 12f, textPaint)
            }
        } else {
            textPaint.color = Color.GRAY
            canvas2.drawText("Masukkan data untuk melihat grafik tren.", 200f, graphTop + 70f, textPaint)
        }

        // Draw Footer Page 2
        textPaint.color = Color.GRAY
        textPaint.textSize = 9f
        canvas2.drawText("Sales Data Explorer - Halaman 2 dari 2", 230f, 825f, textPaint)

        pdfDocument.finishPage(page2)

        val format = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val filename = "Sales_Report_${summary.sku}_${format.format(Date())}.pdf"

        val uri = saveFileToDownloads(context, filename, "application/pdf") { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return uri
    }
}

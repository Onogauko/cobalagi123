package com.example.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

class SalesRepository(
    private val context: Context,
    private val database: SalesDatabase
) {
    private val dao = database.salesDao()

    // Real-time metrics
    val distinctSkuCount: Flow<Int> = dao.getDistinctSkuCount()
    val totalRecordsCount: Flow<Int> = dao.getTotalRecordsCount()
    val totalSalesQty: Flow<Int> = dao.getTotalSalesQty()
    val totalSalesAmount: Flow<Double> = dao.getTotalSalesAmount()
    val lastImportDate: Flow<String?> = dao.getLastImportDate()

    suspend fun clearAllData() {
        withContext(Dispatchers.IO) {
            dao.clearAll()
        }
    }

    suspend fun searchSkus(query: String): List<SkuSearchResult> {
        return withContext(Dispatchers.IO) {
            val formattedQuery = "%${query.trim()}%"
            dao.searchSkus(formattedQuery)
        }
    }

    suspend fun getRecordsForSku(sku: String): List<SalesRecord> {
        return withContext(Dispatchers.IO) {
            dao.getRecordsForSku(sku)
        }
    }

    suspend fun getRecordsForSkuWithFilter(sku: String, startDate: String, endDate: String): List<SalesRecord> {
        return withContext(Dispatchers.IO) {
            dao.getRecordsForSkuWithFilter(sku, startDate, endDate)
        }
    }

    // Analytics Dashboard
    suspend fun getTopSkusByQty(): List<SkuSearchResult> = withContext(Dispatchers.IO) {
        dao.getTopSkusByQty()
    }

    suspend fun getTopSkusByAmount(): List<SkuSearchResult> = withContext(Dispatchers.IO) {
        dao.getTopSkusByAmount()
    }

    suspend fun getDeptsSummaryByAmount(): List<DeptSummary> = withContext(Dispatchers.IO) {
        dao.getDeptsSummaryByAmount()
    }

    suspend fun getDeptsSummaryByQty(): List<DeptSummary> = withContext(Dispatchers.IO) {
        dao.getDeptsSummaryByQty()
    }

    suspend fun getTopItemsForDept(dept: String): List<SkuSearchResult> = withContext(Dispatchers.IO) {
        dao.getTopItemsForDept(dept)
    }

    suspend fun getDistinctDepartments(): List<String> = withContext(Dispatchers.IO) {
        dao.getDistinctDepartments()
    }

    // Excel Import Logic & DB Commits
    suspend fun importExcelUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var recordsImported = 0
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                return@withContext ImportResult.Error("Gagal membuka file Excel.")
            }

            val records = parseExcel(inputStream)
            if (records.isEmpty()) {
                return@withContext ImportResult.Error("Tidak ada baris data penjualan valid yang terbaca.")
            }

            // Bulk Insert inside database transaction
            database.withTransaction {
                // To keep database clean and not duplicate if user wants, wait, user said: "JANGAN melakukan update atau overwrite berdasarkan SKU. Setiap baris Excel adalah data transaksi sales yang harus disimpan sebagai record baru."
                // So we always add them as new records!
                val chunkSize = 1000
                records.chunked(chunkSize).forEach { chunk ->
                    dao.insertAll(chunk)
                }
            }

            recordsImported = records.size
            val duration = (System.currentTimeMillis() - startTime) / 1000.0
            val formattedDuration = String.format(Locale.getDefault(), "%.1f detik", duration)

            ImportResult.Success(
                rowCount = recordsImported,
                duration = formattedDuration
            )
        } catch (e: IllegalArgumentException) {
            ImportResult.Error(e.message ?: "Format kolom Excel salah.")
        } catch (e: Exception) {
            ImportResult.Error("Gagal mengimpor file: ${e.localizedMessage}")
        }
    }

    private fun parseExcel(inputStream: InputStream): List<SalesRecord> {
        val records = mutableListOf<SalesRecord>()
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)
        if (sheet == null || sheet.physicalNumberOfRows == 0) {
            workbook.close()
            return emptyList()
        }

        // Identify headers by name (case insensitive)
        val headerRow = sheet.getRow(0) ?: throw IllegalArgumentException("Excel tidak memiliki baris header.")
        var dateCol = -1
        var deptCol = -1
        var skuCol = -1
        var descCol = -1
        var qtyCol = -1
        var amountCol = -1

        for (i in 0 until headerRow.lastCellNum) {
            val cell = headerRow.getCell(i) ?: continue
            val headerVal = cell.toString().trim()
            when {
                headerVal.equals("Date", ignoreCase = true) -> dateCol = i
                headerVal.equals("Dept", ignoreCase = true) -> deptCol = i
                headerVal.equals("SKU", ignoreCase = true) -> skuCol = i
                headerVal.equals("Item Description", ignoreCase = true) -> descCol = i
                headerVal.equals("Sales Qty", ignoreCase = true) -> qtyCol = i
                headerVal.equals("Sales Amount", ignoreCase = true) -> amountCol = i
            }
        }

        if (dateCol == -1 || deptCol == -1 || skuCol == -1 || descCol == -1 || qtyCol == -1 || amountCol == -1) {
            workbook.close()
            throw IllegalArgumentException("Header tidak lengkap! Kolom wajib: Date, Dept, SKU, Item Description, Sales Qty, Sales Amount.")
        }

        // Parse records
        for (r in 1..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue
            if (isRowEmpty(row)) continue

            try {
                val dateStrRaw = getCellValueAsString(row.getCell(dateCol))
                val deptStr = getCellValueAsString(row.getCell(deptCol)).trim()
                val skuStr = getCellValueAsString(row.getCell(skuCol)).trim()
                val descStr = getCellValueAsString(row.getCell(descCol)).trim()
                val qtyInt = getCellValueAsInt(row.getCell(qtyCol))
                val amountDouble = getCellValueAsDouble(row.getCell(amountCol))

                val formattedDate = formatExcelDate(dateStrRaw)

                if (formattedDate.isNotEmpty() && skuStr.isNotEmpty()) {
                    records.add(
                        SalesRecord(
                            date = formattedDate,
                            dept = if (deptStr.isEmpty()) "Unknown" else deptStr,
                            sku = skuStr,
                            itemDescription = if (descStr.isEmpty()) "Unknown Item" else descStr,
                            salesQty = qtyInt,
                            salesAmount = amountDouble
                        )
                    )
                }
            } catch (e: Exception) {
                // Row-level tolerance
            }
        }

        workbook.close()
        return records
    }

    private fun isRowEmpty(row: Row): Boolean {
        for (c in row.firstCellNum until row.lastCellNum) {
            val cell = row.getCell(c)
            if (cell != null && cell.cellType != CellType.BLANK && cell.toString().trim().isNotEmpty()) {
                return false
            }
        }
        return true
    }

    private fun getCellValueAsString(cell: org.apache.poi.ss.usermodel.Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.NUMERIC -> {
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    val date = cell.dateCellValue
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    sdf.format(date)
                } else {
                    val num = cell.numericCellValue
                    if (num == num.toLong().toDouble()) {
                        num.toLong().toString()
                    } else {
                        num.toString()
                    }
                }
            }
            CellType.STRING -> cell.stringCellValue
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            else -> cell.toString()
        }.trim()
    }

    private fun getCellValueAsInt(cell: org.apache.poi.ss.usermodel.Cell?): Int {
        if (cell == null) return 0
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue.toInt()
            CellType.STRING -> cell.stringCellValue.trim().toDoubleOrNull()?.toInt() ?: 0
            else -> cell.toString().trim().toDoubleOrNull()?.toInt() ?: 0
        }
    }

    private fun getCellValueAsDouble(cell: org.apache.poi.ss.usermodel.Cell?): Double {
        if (cell == null) return 0.0
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> cell.stringCellValue.trim().toDoubleOrNull() ?: 0.0
            else -> cell.toString().trim().toDoubleOrNull() ?: 0.0
        }
    }

    private fun formatExcelDate(dateStr: String): String {
        val clean = dateStr.trim()
        if (clean.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            return clean
        }
        
        // formats to attempt
        val formats = listOf(
            "dd/MM/yyyy",
            "d/M/yyyy",
            "dd-MM-yyyy",
            "yyyy/MM/dd",
            "MM/dd/yyyy"
        )
        for (fmt in formats) {
            try {
                val parser = SimpleDateFormat(fmt, Locale.US)
                val d = parser.parse(clean)
                if (d != null) {
                    val out = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    return out.format(d)
                }
            } catch (e: Exception) {
                // Fallthrough and try another format
            }
        }
        return clean
    }
}

sealed interface ImportResult {
    data class Success(val rowCount: Int, val duration: String) : ImportResult
    data class Error(val message: String) : ImportResult
}

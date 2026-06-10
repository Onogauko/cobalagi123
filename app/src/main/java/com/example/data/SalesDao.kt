package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class SkuSearchResult(
    val sku: String,
    val itemDescription: String,
    val dept: String,
    val totalQty: Int,
    val totalAmount: Double
)

data class DeptSummary(
    val dept: String,
    val totalQty: Int,
    val totalAmount: Double
)

@Dao
interface SalesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<SalesRecord>)

    @Query("DELETE FROM sales_records")
    suspend fun clearAll()

    // Real-time Dashboard Flows
    @Query("SELECT COUNT(DISTINCT sku) FROM sales_records")
    fun getDistinctSkuCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sales_records")
    fun getTotalRecordsCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(salesQty), 0) FROM sales_records")
    fun getTotalSalesQty(): Flow<Int>

    @Query("SELECT COALESCE(SUM(salesAmount), 0.0) FROM sales_records")
    fun getTotalSalesAmount(): Flow<Double>

    @Query("SELECT MAX(date) FROM sales_records")
    fun getLastImportDate(): Flow<String?>

    // Search and Autocomplete Aggregations
    @Query("""
        SELECT sku, MAX(itemDescription) as itemDescription, MAX(dept) as dept, SUM(salesQty) as totalQty, SUM(salesAmount) as totalAmount 
        FROM sales_records 
        WHERE sku LIKE :query OR itemDescription LIKE :query 
        GROUP BY sku 
        ORDER BY totalAmount DESC
    """)
    suspend fun searchSkus(query: String): List<SkuSearchResult>

    // Retrieve Single SKU aggregate and transactions
    @Query("SELECT * FROM sales_records WHERE sku = :sku ORDER BY date ASC")
    suspend fun getRecordsForSku(sku: String): List<SalesRecord>

    @Query("SELECT * FROM sales_records WHERE sku = :sku AND date >= :startDate AND date <= :endDate ORDER BY date ASC")
    suspend fun getRecordsForSkuWithFilter(sku: String, startDate: String, endDate: String): List<SalesRecord>

    // Analytics Dashboard High-Performance Queries
    @Query("""
        SELECT sku, MAX(itemDescription) as itemDescription, MAX(dept) as dept, SUM(salesQty) as totalQty, SUM(salesAmount) as totalAmount 
        FROM sales_records 
        GROUP BY sku 
        ORDER BY totalQty DESC 
        LIMIT 10
    """)
    suspend fun getTopSkusByQty(): List<SkuSearchResult>

    @Query("""
        SELECT sku, MAX(itemDescription) as itemDescription, MAX(dept) as dept, SUM(salesQty) as totalQty, SUM(salesAmount) as totalAmount 
        FROM sales_records 
        GROUP BY sku 
        ORDER BY totalAmount DESC 
        LIMIT 10
    """)
    suspend fun getTopSkusByAmount(): List<SkuSearchResult>

    @Query("""
        SELECT dept, SUM(salesQty) as totalQty, SUM(salesAmount) as totalAmount 
        FROM sales_records 
        GROUP BY dept 
        ORDER BY totalAmount DESC
    """)
    suspend fun getDeptsSummaryByAmount(): List<DeptSummary>

    @Query("""
        SELECT dept, SUM(salesQty) as totalQty, SUM(salesAmount) as totalAmount 
        FROM sales_records 
        GROUP BY dept 
        ORDER BY totalQty DESC
    """)
    suspend fun getDeptsSummaryByQty(): List<DeptSummary>

    @Query("""
        SELECT sku, MAX(itemDescription) as itemDescription, dept, SUM(salesQty) as totalQty, SUM(salesAmount) as totalAmount 
        FROM sales_records 
        WHERE dept = :dept 
        GROUP BY sku 
        ORDER BY totalAmount DESC 
        LIMIT 10
    """)
    suspend fun getTopItemsForDept(dept: String): List<SkuSearchResult>

    @Query("SELECT DISTINCT dept FROM sales_records ORDER BY dept ASC")
    suspend fun getDistinctDepartments(): List<String>
}

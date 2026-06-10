package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sales_records",
    indices = [
        Index(value = ["sku"]),
        Index(value = ["itemDescription"]),
        Index(value = ["date"]),
        Index(value = ["sku", "date"])
    ]
)
data class SalesRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,            // format: YYYY-MM-DD
    val dept: String,
    val sku: String,
    val itemDescription: String,
    val salesQty: Int,
    val salesAmount: Double
)

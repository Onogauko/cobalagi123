package com.example.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.DailySalesAggregate
import com.example.ui.SalesViewModel
import com.example.ui.export.Exporter
import com.example.ui.model.DateFilter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    sku: String,
    viewModel: SalesViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val skuDetails by viewModel.selectedSkuDetails.collectAsStateWithLifecycle()
    val activeFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val isSortAscending by viewModel.isDateSortAscending.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Sales By Date, 1: Sales By Month
    var showCustomDateDialog by remember { mutableStateOf(false) }

    // Selected custom range display
    var customStartStr by remember { mutableStateOf("2026-06-01") }
    var customEndStr by remember { mutableStateOf("2026-06-10") }

    // Init SKU details injection
    LaunchedEffect(sku) {
        viewModel.selectSku(sku)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analisis SKU", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            skuDetails?.let { detail ->
                                val uri = Exporter.exportToExcel(context, detail)
                                if (uri != null) {
                                    Toast.makeText(context, "Excel disimpan di folder Download/SalesExplorer!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Gagal export Excel.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("export_excel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Export Excel",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(
                        onClick = {
                            skuDetails?.let { detail ->
                                val uri = Exporter.exportToPdf(context, detail)
                                if (uri != null) {
                                    Toast.makeText(context, "PDF disimpan di folder Download/SalesExplorer!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Gagal export PDF.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("export_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Export PDF",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (skuDetails == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Memuat data transaksi SKU...", color = Color.Gray)
                    }
                }
            } else {
                val detail = skuDetails!!

                // Horizontal Filter Chooser
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateFilter.values().forEach { filter ->
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick = {
                                if (filter == DateFilter.CUSTOM) {
                                    showCustomDateDialog = true
                                } else {
                                    viewModel.updateFilter(filter)
                                }
                            },
                            label = { Text(filter.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.0f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Item Title and Description Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(detail.dept.uppercase(), fontWeight = FontWeight.Bold, fontSize = 10.sp) }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        "SKU #${detail.sku}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    detail.itemDescription,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Key Statistics Grid (Total Qty, Total Amount, Avg Qty, Avg Amount, High, Low)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Indikator Performa SKU", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatSubCard(
                                    label = "Total Qty Terjual",
                                    value = viewModel.formatNumber(detail.totalQty),
                                    desc = "Kuantitas unit",
                                    icon = Icons.Default.ShoppingCart,
                                    modifier = Modifier.weight(1f)
                                )
                                StatSubCard(
                                    label = "Total Nominal",
                                    value = viewModel.formatRupiah(detail.totalAmount),
                                    desc = "Nilai omset",
                                    icon = Icons.Default.Money,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatSubCard(
                                    label = "Rata-rata Harian Qty",
                                    value = String.format(Locale.US, "%.1f", detail.avgDailyQty),
                                    desc = "Unit / hari",
                                    icon = Icons.Default.Timer,
                                    modifier = Modifier.weight(1f)
                                )
                                StatSubCard(
                                    label = "Rata-rata Harian Nilai",
                                    value = viewModel.formatRupiah(detail.avgDailyAmount),
                                    desc = "Omset / hari",
                                    icon = Icons.Default.AccountBalance,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatSubCard(
                                    label = "Penjualan Tertinggi",
                                    value = viewModel.formatRupiah(detail.highestDayAmount),
                                    desc = detail.highestDayDate,
                                    icon = Icons.Default.KeyboardArrowUp,
                                    modifier = Modifier.weight(1f)
                                )
                                StatSubCard(
                                    label = "Penjualan Terendah",
                                    value = viewModel.formatRupiah(detail.lowestDayAmount),
                                    desc = detail.lowestDayDate,
                                    icon = Icons.Default.KeyboardArrowDown,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Interactive Line Charts
                    item {
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Grafik Tren Volume Penjualan",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Tren kuantitas harian (Limit 15 transaksi)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                ) {
                                    CustomLineChart(
                                        data = detail.dailySales.takeLast(15),
                                        getValue = { it.qty.toDouble() },
                                        lineColor = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Grafik Tren Nilai Penjualan",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Tren nominal omset harian (Limit 15 transaksi)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                ) {
                                    CustomLineChart(
                                        data = detail.dailySales.takeLast(15),
                                        getValue = { it.amount },
                                        lineColor = Color(0xFFFF9800)
                                    )
                                }
                            }
                        }
                    }

                    // Segment Tab Chooser (Date vs Month)
                    item {
                        Column {
                            TabRow(selectedTabIndex = activeTab) {
                                Tab(
                                    selected = activeTab == 0,
                                    onClick = { activeTab = 0 },
                                    text = { Text("Harian (Date)") }
                                )
                                Tab(
                                    selected = activeTab == 1,
                                    onClick = { activeTab = 1 },
                                    text = { Text("Bulanan (Month)") }
                                )
                            }
                        }
                    }

                    // Content based on selected Tab
                    if (activeTab == 0) {
                        // Table row header
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { viewModel.toggleDateSort() }
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        "Tanggal Transaksi",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Icon(
                                        imageVector = if (isSortAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Sort",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text("Qty", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Sales Amount", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                            HorizontalDivider()
                        }

                        if (detail.dailySales.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Tidak ada hasil transaksi harian.", color = Color.Gray)
                                }
                            }
                        } else {
                            items(detail.dailySales, key = { it.date }) { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        viewModel.formatDateLabel(row.date),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        viewModel.formatNumber(row.qty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        viewModel.formatRupiah(row.amount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                            }
                        }

                    } else {
                        // Monthly Cumulative Lists
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Bulan-Tahun", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Total Qty", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Kumulatif Sales", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                            HorizontalDivider()
                        }

                        if (detail.monthlySales.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Tidak ada data bulanan.", color = Color.Gray)
                                }
                            }
                        } else {
                            items(detail.monthlySales, key = { it.monthKey }) { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(row.monthLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(viewModel.formatNumber(row.qty), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        viewModel.formatRupiah(row.amount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1565C0)
                                    )
                                }
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(48.dp)) }
                }
            }
        }

        // Custom Date Picker Dialog Trigger
        if (showCustomDateDialog) {
            val calendar = Calendar.getInstance()
            
            // Native Date picker logic
            val showDatePicker = { isStart: Boolean ->
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        val cal = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                        val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                        if (isStart) {
                            customStartStr = formatted
                        } else {
                            customEndStr = formatted
                            // Submit filter updates
                            viewModel.updateFilter(DateFilter.CUSTOM, Pair(customStartStr, customEndStr))
                            showCustomDateDialog = false
                        }
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }

            AlertDialog(
                onDismissRequest = { showCustomDateDialog = false },
                title = { Text("Filter Jangkauan Tanggal") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Tentukan tanggal awal dan tanggal akhir data transaksi untuk dihitung.")
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(onClick = { showDatePicker(true) }) {
                                Text("Awal: $customStartStr")
                            }
                            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "ke")
                            Button(onClick = { showDatePicker(false) }) {
                                Text("Akhir: $customEndStr")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateFilter(DateFilter.CUSTOM, Pair(customStartStr, customEndStr))
                        showCustomDateDialog = false
                    }) {
                        Text("Terapkan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomDateDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }
    }
}

@Composable
fun StatSubCard(
    label: String,
    value: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.0f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Vector Line chart plotting in Compose Canvas
@Composable
fun CustomLineChart(
    data: List<DailySalesAggregate>,
    getValue: (DailySalesAggregate) -> Double,
    lineColor: Color
) {
    if (data.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Tidak cukup data tren untuk digambar.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        return
    }

    val maxVal = data.maxOf { getValue(it) }.coerceAtLeast(1.0)
    val minVal = 0.0

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val paddingLeft = 10f
        val paddingRight = 10f
        val paddingTop = 15f
        val paddingBottom = 15f

        val usableWidth = width - paddingLeft - paddingRight
        val usableHeight = height - paddingTop - paddingBottom

        val stepX = if (data.size > 1) usableWidth / (data.size - 1) else usableWidth
        val rangeY = maxVal - minVal

        val pointsCoordinates = data.mapIndexed { idx, item ->
            val x = paddingLeft + (stepX * idx)
            val currVal = getValue(item)
            val normalizedY = ((currVal - minVal) / rangeY)
            val y = paddingTop + usableHeight - (normalizedY * usableHeight)
            Offset(x, y.toFloat())
        }

        // Draw background horizontal baseline and maxline
        drawLine(
            color = Color.LightGray.copy(alpha = 0.4f),
            start = Offset(0f, paddingTop + usableHeight),
            end = Offset(width, paddingTop + usableHeight),
            strokeWidth = 2f
        )
        drawLine(
            color = Color.LightGray.copy(alpha = 0.2f),
            start = Offset(0f, paddingTop),
            end = Offset(width, paddingTop),
            strokeWidth = 1f
        )

        // Plot path lines
        val linePath = Path().apply {
            if (pointsCoordinates.isNotEmpty()) {
                val first = pointsCoordinates.first()
                moveTo(first.x, first.y)
                for (i in 1 until pointsCoordinates.size) {
                    val p = pointsCoordinates[i]
                    lineTo(p.x, p.y)
                }
            }
        }

        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 6f)
        )

        // Draw node circles
        pointsCoordinates.forEach { p ->
            drawCircle(
                color = lineColor,
                radius = 8f,
                center = p
            )
            drawCircle(
                color = Color.White,
                radius = 4f,
                center = p
            )
        }
    }
}

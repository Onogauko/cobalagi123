package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.example.data.SkuSearchResult
import java.util.Locale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.SalesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: SalesViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    var activeSubTab by remember { mutableIntStateOf(0) } // 0: Top SKUs, 1: Departments

    val topQtySkus by viewModel.topSkusByQty.collectAsStateWithLifecycle()
    val topAmountSkus by viewModel.topSkusByAmount.collectAsStateWithLifecycle()
    val deptsSummary by viewModel.deptsSummary.collectAsStateWithLifecycle()

    val departmentsList by viewModel.departmentsList.collectAsStateWithLifecycle()
    val selectedDept by viewModel.selectedDept.collectAsStateWithLifecycle()
    val deptTopItems by viewModel.deptTopItems.collectAsStateWithLifecycle()

    var showDeptPicker by remember { mutableStateOf(false) }

    // Auto-reload on launching screen
    LaunchedEffect(Unit) {
        viewModel.loadAnalyticsData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Peringkat & Analitika", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
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
            // Screen Sub Tab choosing
            TabRow(selectedTabIndex = activeSubTab) {
                Tab(
                    selected = activeSubTab == 0,
                    onClick = { activeSubTab = 0 },
                    text = { Text("Top 10 SKU") },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Top 10", modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = activeSubTab == 1,
                    onClick = { activeSubTab = 1 },
                    text = { Text("Analisis Department") },
                    icon = { Icon(Icons.Default.Menu, contentDescription = "Dept", modifier = Modifier.size(16.dp)) }
                )
            }

            if (topQtySkus.isEmpty() && topAmountSkus.isEmpty() && deptsSummary.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Empty",
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Belum ada data analitika terbaca.", color = Color.Gray)
                        Text("Silakan jalankan import Excel terlebih dahulu.", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            } else {
                AnimatedContent(
                    targetState = activeSubTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "analytics_view_toggle",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.0f)
                ) { targetTab ->
                    if (targetTab == 0) {
                        // Top SKUs Ranking list
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item { Spacer(modifier = Modifier.height(8.dp)) }

                            // COLUMN 1: TOP 10 BY QUANTITY
                            item {
                                Text(
                                    "Top 10 SKU - Berdasarkan Kuantitas (Qty)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            itemsIndexed(topQtySkus) { rank, item ->
                                RankItemRow(
                                    rank = rank + 1,
                                    skuName = item.itemDescription,
                                    skuVal = item.sku,
                                    dept = item.dept,
                                    subText = "Kuantitas: ${viewModel.formatNumber(item.totalQty)} unit",
                                    amountText = viewModel.formatRupiah(item.totalAmount),
                                    onClicked = { onNavigateToDetail(item.sku) }
                                )
                            }

                            // COLUMN 2: TOP 10 BY AMOUNT
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Top 10 SKU - Berdasarkan Nilai Penjualan",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            itemsIndexed(topAmountSkus) { rank, item ->
                                RankItemRow(
                                    rank = rank + 1,
                                    skuName = item.itemDescription,
                                    skuVal = item.sku,
                                    dept = item.dept,
                                    subText = "Nominal: ${viewModel.formatRupiah(item.totalAmount)}",
                                    amountText = "${viewModel.formatNumber(item.totalQty)} qty",
                                    onClicked = { onNavigateToDetail(item.sku) }
                                )
                            }

                            item { Spacer(modifier = Modifier.height(32.dp)) }
                        }
                    } else {
                        // Department Summary & top items per department
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item { Spacer(modifier = Modifier.height(8.dp)) }

                            item {
                                Text(
                                    "Kontribusi Penjualan per Department",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Persentase kontribusi dihitung berdasarkan total nominal penjualan.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // Calculate total of all departments amount
                            val totalDeptsAmount = deptsSummary.sumOf { it.totalAmount }.coerceAtLeast(1.0)

                            itemsIndexed(deptsSummary) { index, dept ->
                                val contributionPercent = (dept.totalAmount / totalDeptsAmount) * 100
                                DepartmentMetricRow(
                                    deptName = dept.dept,
                                    qty = dept.totalQty,
                                    amount = dept.totalAmount,
                                    percent = contributionPercent.toFloat(),
                                    viewModel = viewModel
                                )
                            }

                            // TOP 10 ITEMS PER SELECTED DEPARTMENT
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Top 10 Item dalam Department",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Department Aktif: ${selectedDept ?: "Belum Dipilih"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    
                                    Button(
                                        onClick = { showDeptPicker = true },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Pilih Dept")
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand")
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            if (deptTopItems.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Tidak ada item data untuk department ini.", color = Color.Gray)
                                    }
                                }
                            } else {
                                itemsIndexed(deptTopItems) { rank, item ->
                                    RankItemRow(
                                        rank = rank + 1,
                                        skuName = item.itemDescription,
                                        skuVal = item.sku,
                                        dept = item.dept,
                                        subText = "Kuantitas: ${viewModel.formatNumber(item.totalQty)} unit",
                                        amountText = viewModel.formatRupiah(item.totalAmount),
                                        onClicked = { onNavigateToDetail(item.sku) }
                                    )
                                }
                            }

                            item { Spacer(modifier = Modifier.height(48.dp)) }
                        }
                    }
                }
            }
        }

        // Department Picker Popup Dialog
        if (showDeptPicker) {
            AlertDialog(
                onDismissRequest = { showDeptPicker = false },
                title = { Text("Pilih Department") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                    ) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(departmentsList) { dept ->
                                Text(
                                    text = dept,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectDepartmentForDetails(dept)
                                            showDeptPicker = false
                                        }
                                        .padding(12.dp)
                                )
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDeptPicker = false }) {
                        Text("Tutup")
                    }
                }
            )
        }
    }
}

@Composable
fun RankItemRow(
    rank: Int,
    skuName: String,
    skuVal: String,
    dept: String,
    subText: String,
    amountText: String,
    onClicked: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClicked() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle Badge showing rank
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (rank <= 3) MaterialTheme.colorScheme.primary else Color.LightGray
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rank.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        skuName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        dept,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 9.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SKU: $skuVal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        subText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                amountText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black,
                color = Color(0xFF2E7D32)
            )
        }
    }
}

@Composable
fun DepartmentMetricRow(
    deptName: String,
    qty: Int,
    amount: Double,
    percent: Float,
    viewModel: SalesViewModel
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    deptName.uppercase(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    String.format(Locale.US, "%.1f%%", percent),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progressive visual bar
            LinearProgressIndicator(
                progress = { percent / 100f },
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.LightGray.copy(alpha = 0.3f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Unit Terjual", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text(viewModel.formatNumber(qty), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total Omset Rupiah", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text(viewModel.formatRupiah(amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }
            }
        }
    }
}

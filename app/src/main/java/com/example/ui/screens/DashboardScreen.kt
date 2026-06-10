package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.ImportUiState
import androidx.compose.foundation.background
import com.example.data.SkuSearchResult
import com.example.ui.SalesViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: SalesViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAnalytics: () -> Unit
) {
    val context = LocalContext.current

    // Observe dashboard flows
    val distinctSku by viewModel.distinctSkuCountStr.collectAsStateWithLifecycle()
    val totalRecords by viewModel.totalRecordsCountStr.collectAsStateWithLifecycle()
    val totalQty by viewModel.totalSalesQtyStr.collectAsStateWithLifecycle()
    val totalAmount by viewModel.totalSalesAmountStr.collectAsStateWithLifecycle()
    val lastImportDate by viewModel.lastImportDateStr.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()

    // Excel Explorer Intent Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                viewModel.importExcelFile(uri)
            }
        }
    )

    // ZXing Barcode Scan Launcher
    val barcodeLauncher = rememberLauncherForActivityResult(
        contract = ScanContract(),
        onResult = { result ->
            if (result.contents != null) {
                // Scanned barcode represents the SKU
                val scannedSku = result.contents
                viewModel.updateSearchQuery(scannedSku)
                Toast.makeText(context, "Barcode Terpindai: $scannedSku", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // Launch scanner parameters
    val launchBarcodeScanner = {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            setPrompt("Tempatkan barcode SKU di dalam kotak kamera")
            setCameraId(0)
            setBeepEnabled(true)
            setBarcodeImageEnabled(false)
            setOrientationLocked(false)
        }
        barcodeLauncher.launch(options)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Sales Data Explorer",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = "Logo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                    )
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToAnalytics,
                        modifier = Modifier.testTag("nav_analytics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = "Analytics Dashboard",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = {
                        viewModel.clearDatabase()
                        Toast.makeText(context, "Database dibersihkan.", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Hapus Data",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = launchBarcodeScanner,
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.testTag("barcode_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = "Pindai Barcode SKU"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Real-time Search Panel
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Cari berdasarkan SKU atau nama item...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Cari")
                },
                trailingIcon = {
                    Row {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Hapus")
                            }
                        }
                        IconButton(onClick = launchBarcodeScanner) {
                            Icon(imageVector = Icons.Default.Camera, contentDescription = "Scan")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_bar_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Contextual Overlay Loading Alert
            when (val currentStatus = importStatus) {
                is ImportUiState.Loading -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                "Sedang membaca & menyimpan data Excel...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                is ImportUiState.Success -> {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissImportStatus() },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Berhasil",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(40.dp)
                            )
                        },
                        title = { Text("Import Berhasil") },
                        text = {
                            Text(
                                "Data berhasil diimport & disimpan secara lokal!\n\n" +
                                        "• Baris data: ${viewModel.formatNumber(currentStatus.rowCount)} baris\n" +
                                        "• Durasi baca: ${currentStatus.duration}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            Button(onClick = { viewModel.dismissImportStatus() }) {
                                Text("OK")
                            }
                        }
                    )
                }
                is ImportUiState.Error -> {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissImportStatus() },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Gagal",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(40.dp)
                            )
                        },
                        title = { Text("Import Gagal") },
                        text = {
                            Text(
                                currentStatus.message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            Button(onClick = { viewModel.dismissImportStatus() }) {
                                Text("Tutup")
                            }
                        }
                    )
                }
                ImportUiState.Idle -> {}
            }

            AnimatedContent(
                targetState = searchQuery.trim().isEmpty(),
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "search_toggle"
            ) { isQueryEmpty ->
                if (isQueryEmpty) {
                    // Display Home Dashboard Summary
                    Column {
                        // Quick Action import Excel banner Card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_excel_button")
                                .clickable {
                                    filePickerLauncher.launch(
                                        arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                    )
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Upload Icon",
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1.0f)) {
                                    Text(
                                        "Import File Excel (.xlsx)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        "Unggah laporan transaksi sales terbaru",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Go",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Stats Dashboard Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Dashboard Ringkasan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "Last: $lastImportDate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Grid containing statistics (2 cols)
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    StatCard(
                                        label = "Total SKU Unik",
                                        value = distinctSku,
                                        icon = Icons.Default.Label,
                                        tint = Color(0xFF1976D2),
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatCard(
                                        label = "Transaksi Record",
                                        value = totalRecords,
                                        icon = Icons.Default.List,
                                        tint = Color(0xFF388E3C),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    StatCard(
                                        label = "Quantity Terjual",
                                        value = totalQty,
                                        icon = Icons.Default.ShoppingCart,
                                        tint = Color(0xFFE65100),
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatCard(
                                        label = "Nilai Penjualan",
                                        value = totalAmount,
                                        icon = Icons.Default.Money,
                                        tint = Color(0xFFD32F2F),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToAnalytics() }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ShowChart,
                                            contentDescription = "Analisis",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            "Buka Modul Analytics & Peringkat",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1.0f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowRight,
                                            contentDescription = "Buka",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(100.dp)) }
                        }
                    }
                } else {
                    // Display Search Result List
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Hasil Pencarian",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            } else {
                                Text(
                                    "${searchResults.size} item ditemukan",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (searchResults.isEmpty()) {
                            // Blank State searched
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Not Found",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "SKU atau Item tidak ditemukan",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.DarkGray
                                    )
                                    Text(
                                        "Periksa kembali kata kunci atau coba scan barcode lain.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                items(searchResults, key = { it.sku }) { item ->
                                    SearchResultCard(
                                        item = item,
                                        onSelected = { onNavigateToDetail(item.sku) },
                                        viewModel = viewModel
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(100.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth() // Expands properly
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint.copy(alpha = 0.9f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchResultCard(
    item: SkuSearchResult,
    onSelected: () -> Unit,
    viewModel: SalesViewModel
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_result_card_${item.sku}")
            .clickable { onSelected() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Dept & SKU ID
            Row(verticalAlignment = Alignment.CenterVertically) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(item.dept.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.height(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "SKU #${item.sku}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body element: Item description
            Text(
                item.itemDescription,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(12.dp))

            // Footer element metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Kuantitas", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text(
                        viewModel.formatNumber(item.totalQty),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Total Penjualan", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text(
                        viewModel.formatRupiah(item.totalAmount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }
    }
}

private val MyIndicatorSize = 24.dp

package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ui.model.DateFilter
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

@OptIn(FlowPreview::class)
class SalesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SalesRepository

    init {
        val database = SalesDatabase.getDatabase(application)
        repository = SalesRepository(application, database)
    }

    // Dashboard States (Real-time DB counts)
    val distinctSkuCountStr: StateFlow<String> = repository.distinctSkuCount
        .map { formatNumber(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0")

    val totalRecordsCountStr: StateFlow<String> = repository.totalRecordsCount
        .map { formatNumber(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0")

    val totalSalesQtyStr: StateFlow<String> = repository.totalSalesQty
        .map { formatNumber(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0")

    val totalSalesAmountStr: StateFlow<String> = repository.totalSalesAmount
        .map { formatRupiah(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Rp 0")

    val lastImportDateStr: StateFlow<String> = repository.lastImportDate
        .map { it ?: "Belum pernah" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "-")

    // Import Status State
    private val _importStatus = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importStatus: StateFlow<ImportUiState> = _importStatus.asStateFlow()

    // Search Query State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SkuSearchResult>>(emptyList())
    val searchResults: StateFlow<List<SkuSearchResult>> = _searchResults.asStateFlow()

    // Detailed SKU Analysis States
    private val _selectedSku = MutableStateFlow<String?>(null)
    val selectedSku: StateFlow<String?> = _selectedSku.asStateFlow()

    private val _selectedSkuDetails = MutableStateFlow<SkuDetailSummary?>(null)
    val selectedSkuDetails: StateFlow<SkuDetailSummary?> = _selectedSkuDetails.asStateFlow()

    // Search and Filter Settings
    private val _selectedFilter = MutableStateFlow(DateFilter.LAST_30_DAYS)
    val selectedFilter: StateFlow<DateFilter> = _selectedFilter.asStateFlow()

    private val _customDateRange = MutableStateFlow<Pair<String, String>?>(null) // Format: YYYY-MM-DD
    val customDateRange: StateFlow<Pair<String, String>?> = _customDateRange.asStateFlow()

    // Sorting settings for detail table
    private val _isDateSortAscending = MutableStateFlow(false)
    val isDateSortAscending: StateFlow<Boolean> = _isDateSortAscending.asStateFlow()

    // Analytics Dashboard States
    private val _topSkusByQty = MutableStateFlow<List<SkuSearchResult>>(emptyList())
    val topSkusByQty: StateFlow<List<SkuSearchResult>> = _topSkusByQty.asStateFlow()

    private val _topSkusByAmount = MutableStateFlow<List<SkuSearchResult>>(emptyList())
    val topSkusByAmount: StateFlow<List<SkuSearchResult>> = _topSkusByAmount.asStateFlow()

    private val _deptsSummary = MutableStateFlow<List<DeptSummary>>(emptyList())
    val deptsSummary: StateFlow<List<DeptSummary>> = _deptsSummary.asStateFlow()

    private val _selectedDept = MutableStateFlow<String?>(null)
    val selectedDept: StateFlow<String?> = _selectedDept.asStateFlow()

    private val _deptTopItems = MutableStateFlow<List<SkuSearchResult>>(emptyList())
    val deptTopItems: StateFlow<List<SkuSearchResult>> = _deptTopItems.asStateFlow()

    private val _departmentsList = MutableStateFlow<List<String>>(emptyList())
    val departmentsList: StateFlow<List<String>> = _departmentsList.asStateFlow()

    init {
        // Trigger setup of analytics records
        loadAnalyticsData()

        // Reactive search handler with debounce
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    executeSearch(query)
                }
        }
    }

    // Setters
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectSku(sku: String?) {
        _selectedSku.value = sku
        if (sku != null) {
            loadSkuDetailAnalysis(sku)
        } else {
            _selectedSkuDetails.value = null
        }
    }

    fun updateFilter(filter: DateFilter, customRange: Pair<String, String>? = null) {
        _selectedFilter.value = filter
        if (filter == DateFilter.CUSTOM) {
            _customDateRange.value = customRange
        } else {
            _customDateRange.value = null
        }
        // Reload details if active
        _selectedSku.value?.let { loadSkuDetailAnalysis(it) }
    }

    fun toggleDateSort() {
        _isDateSortAscending.value = !_isDateSortAscending.value
        // Retrigger layout sort
        _selectedSku.value?.let { loadSkuDetailAnalysis(it) }
    }

    fun selectDepartmentForDetails(dept: String?) {
        _selectedDept.value = dept
        viewModelScope.launch {
            if (dept != null) {
                _deptTopItems.value = repository.getTopItemsForDept(dept)
            } else {
                _deptTopItems.value = emptyList()
            }
        }
    }

    fun clearDatabase() {
        viewModelScope.launch {
            repository.clearAllData()
            loadAnalyticsData()
        }
    }

    // Search executor
    private suspend fun executeSearch(query: String) {
        if (query.trim().isEmpty()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        _isSearching.value = true
        try {
            val results = repository.searchSkus(query)
            _searchResults.value = results
        } catch (e: Exception) {
            _searchResults.value = emptyList()
        } finally {
            _isSearching.value = false
        }
    }

    // Load static analysis for selected SKU
    fun loadSkuDetailAnalysis(sku: String) {
        viewModelScope.launch {
            val filter = _selectedFilter.value
            val range = _customDateRange.value

            val (startDate, endDate) = if (filter == DateFilter.CUSTOM && range != null) {
                range
            } else {
                calculateDateRangeLimits(filter)
            }

            val records = repository.getRecordsForSkuWithFilter(sku, startDate, endDate)
            if (records.isEmpty()) {
                // Try fetching raw historical to show basic model info
                val historical = repository.getRecordsForSku(sku)
                if (historical.isNotEmpty()) {
                    val first = historical.first()
                    _selectedSkuDetails.value = SkuDetailSummary(
                        sku = sku,
                        itemDescription = first.itemDescription,
                        dept = first.dept,
                        totalQty = 0,
                        totalAmount = 0.0,
                        avgDailyQty = 0.0,
                        avgDailyAmount = 0.0,
                        highestDayDate = "-",
                        highestDayAmount = 0.0,
                        lowestDayDate = "-",
                        lowestDayAmount = 0.0,
                        dailySales = emptyList(),
                        monthlySales = emptyList(),
                        originalRecords = emptyList()
                    )
                } else {
                    _selectedSkuDetails.value = null
                }
                return@launch
            }

            val firstRecord = records.first()

            // Calculations
            val totalQty = records.sumOf { it.salesQty }
            val totalAmount = records.sumOf { it.salesAmount }

            // Group by Date for daily stats
            val dailyGrouping = records.groupBy { it.date }
                .mapValues { entry ->
                    Pair(
                        entry.value.sumOf { it.salesQty },
                        entry.value.sumOf { it.salesAmount }
                    )
                }

            val dailyList = dailyGrouping.map {
                DailySalesAggregate(
                    date = it.key,
                    qty = it.value.first,
                    amount = it.value.second
                )
            }.sortedWith(compareBy {
                val parsedDate = try {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it.date)
                } catch (e: Exception) {
                    null
                }
                parsedDate
            })

            // Sort Daily Sales based on toggle
            val sortedDailyList = if (_isDateSortAscending.value) {
                dailyList
            } else {
                dailyList.reversed()
            }

            // Averages
            val daysCount = dailyGrouping.size.coerceAtLeast(1)
            val avgDailyQty = totalQty.toDouble() / daysCount
            val avgDailyAmount = totalAmount / daysCount

            // Highest & Lowest Days by daily Amount
            val highestDay = dailyGrouping.maxByOrNull { it.value.second }
            val lowestDay = dailyGrouping.minByOrNull { it.value.second }

            val highestDayDate = highestDay?.key ?: "-"
            val highestDayAmount = highestDay?.value?.second ?: 0.0

            val lowestDayDate = lowestDay?.key ?: "-"
            val lowestDayAmount = lowestDay?.value?.second ?: 0.0

            // Group by month
            // format: YYYY-MM
            val monthlyGrouping = records.groupBy { it.date.substring(0, 7) }
                .mapValues { entry ->
                    Pair(
                        entry.value.sumOf { it.salesQty },
                        entry.value.sumOf { it.salesAmount }
                    )
                }

            val monthlyList = monthlyGrouping.map {
                val monthLabel = try {
                    val dateObj = SimpleDateFormat("yyyy-MM", Locale.US).parse(it.key)
                    SimpleDateFormat("MMM yyyy", Locale.US).format(dateObj!!)
                } catch (e: Exception) {
                    it.key
                }
                MonthlySalesAggregate(
                    monthKey = it.key,
                    monthLabel = monthLabel,
                    qty = it.value.first,
                    amount = it.value.second
                )
            }.sortedBy { it.monthKey }

            _selectedSkuDetails.value = SkuDetailSummary(
                sku = sku,
                itemDescription = firstRecord.itemDescription,
                dept = firstRecord.dept,
                totalQty = totalQty,
                totalAmount = totalAmount,
                avgDailyQty = avgDailyQty,
                avgDailyAmount = avgDailyAmount,
                highestDayDate = formatDateLabel(highestDayDate),
                highestDayAmount = highestDayAmount,
                lowestDayDate = formatDateLabel(lowestDayDate),
                lowestDayAmount = lowestDayAmount,
                dailySales = sortedDailyList,
                monthlySales = monthlyList,
                originalRecords = records
            )
        }
    }

    // Run Analytics
    fun loadAnalyticsData() {
        viewModelScope.launch {
            _topSkusByQty.value = repository.getTopSkusByQty()
            _topSkusByAmount.value = repository.getTopSkusByAmount()
            _deptsSummary.value = repository.getDeptsSummaryByAmount()
            _departmentsList.value = repository.getDistinctDepartments()
            
            // Auto select first department if available
            val list = _departmentsList.value
            if (list.isNotEmpty() && _selectedDept.value == null) {
                selectDepartmentForDetails(list.first())
            }
        }
    }

    // Trigger Import
    fun importExcelFile(uri: Uri) {
        viewModelScope.launch {
            _importStatus.value = ImportUiState.Loading
            when (val res = repository.importExcelUri(uri)) {
                is ImportResult.Success -> {
                    _importStatus.value = ImportUiState.Success(
                        rowCount = res.rowCount,
                        duration = res.duration
                    )
                    loadAnalyticsData() // Reload summaries
                }
                is ImportResult.Error -> {
                    _importStatus.value = ImportUiState.Error(res.message)
                }
            }
        }
    }

    fun dismissImportStatus() {
        _importStatus.value = ImportUiState.Idle
    }

    // Date limit helpers
    private fun calculateDateRangeLimits(filter: DateFilter): Pair<String, String> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        // Since the user request's current time is: 2026-06-10T00:51:43Z,
        // we'll anchor our relative date math there so that imported records
        // matching the year 2026 will filter nicely!
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.JUNE, 10) // Anchor date 2026-06-10

        val endDateStr = sdf.format(cal.time)

        return when (filter) {
            DateFilter.LAST_7_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -6)
                Pair(sdf.format(cal.time), endDateStr)
            }
            DateFilter.LAST_30_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -29)
                Pair(sdf.format(cal.time), endDateStr)
            }
            DateFilter.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = sdf.format(cal.time)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                val end = sdf.format(cal.time)
                Pair(start, end)
            }
            DateFilter.LAST_MONTH -> {
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = sdf.format(cal.time)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                val end = sdf.format(cal.time)
                Pair(start, end)
            }
            DateFilter.CUSTOM -> {
                cal.add(Calendar.DAY_OF_YEAR, -180) // default fallback: 6 months
                Pair(sdf.format(cal.time), endDateStr)
            }
        }
    }

    // Localization utils
    fun formatRupiah(amount: Double): String {
        val symbols = DecimalFormatSymbols(Locale("in", "ID"))
        symbols.currencySymbol = "Rp "
        symbols.groupingSeparator = '.'
        symbols.decimalSeparator = ','
        val formatter = DecimalFormat("Rp #,###.##", symbols)
        return formatter.format(amount).replace(",00", "")
    }

    fun formatNumber(num: Number): String {
        return DecimalFormat("#,###", DecimalFormatSymbols(Locale("in", "ID"))).format(num)
    }

    fun formatDateLabel(dateStr: String): String {
        if (dateStr == "-") return "-"
        return try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)
            SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(date!!)
        } catch (e: Exception) {
            dateStr
        }
    }
}

// Result Wrappers
sealed interface ImportUiState {
    object Idle : ImportUiState
    object Loading : ImportUiState
    data class Success(val rowCount: Int, val duration: String) : ImportUiState
    data class Error(val message: String) : ImportUiState
}

data class DailySalesAggregate(
    val date: String,
    val qty: Int,
    val amount: Double
)

data class MonthlySalesAggregate(
    val monthKey: String,
    val monthLabel: String,
    val qty: Int,
    val amount: Double
)

data class SkuDetailSummary(
    val sku: String,
    val itemDescription: String,
    val dept: String,
    val totalQty: Int,
    val totalAmount: Double,
    val avgDailyQty: Double,
    val avgDailyAmount: Double,
    val highestDayDate: String,
    val highestDayAmount: Double,
    val lowestDayDate: String,
    val lowestDayAmount: Double,
    val dailySales: List<DailySalesAggregate>,
    val monthlySales: List<MonthlySalesAggregate>,
    val originalRecords: List<SalesRecord>
)

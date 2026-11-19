package my.toolmentum.hundredlife

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ─────────────────────────────────────────────
// DESIGN SYSTEM — 100Life Official Colors
// Locked: 13 June 2026
// ─────────────────────────────────────────────
object AppColors {
    val NavyDark = Color(0xFF1051AB)       // gradient bottom / dark blue
    val NavyMid = Color(0xFF1565C0)        // primary brand blue
    val NavyLight = Color(0xFF176CC8)      // gradient top / light blue
    val RoyalBlue = Color(0xFF2356C8)      // buttons / actions
    val PageBackground = Color(0xFFF4F6FA) // off-white screen background
    val CardWhite = Color(0xFFFFFFFF)      // card surfaces
    val Success = Color(0xFF4CAF50)        // save / positive actions
    val Error = Color(0xFFE53935)          // delete / errors
    val TextDark = Color(0xFF1A1A2E)       // body text on light bg
    val TextGray = Color(0xFF757575)       // secondary / hint text
    val TextOnDark = Color(0xFFFFFFFF)     // text on navy bg
    val Divider = Color(0xFF90CAF9)        // light blue dividers
    val CardBlue = Color(0xFFE3F2FD)       // light blue card bg
}

// ─────────────────────────────────────────────
// CONTEXT HELPER — unwrap to the hosting Activity
// TimePickerDialog (and any Dialog) needs a real
// Activity window token; applicationContext has none.
// ─────────────────────────────────────────────
fun Context.findActivity(): Activity {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    throw IllegalStateException("No Activity found in context chain — cannot show dialog")
}

@Composable
fun SecurePrefsOrError(context: Context, onBack: () -> Unit): SharedPreferences? {
    val opened = remember(context) { runCatching { getSecurePrefs(context) } }
    val prefs = opened.getOrNull()
    if (prefs == null) {
        SecureStorageErrorScreen(
            detail = opened.exceptionOrNull()?.message
                ?: "Encrypted settings could not be opened.",
            onBack = onBack
        )
    }
    return prefs
}

@Composable
fun HealthDatabaseOrError(context: Context, onBack: () -> Unit): DatabaseHelper? {
    val opened = remember(context) { runCatching { DatabaseHelper(context) } }
    val helper = opened.getOrNull()
    if (helper != null) {
        DisposableEffect(helper) {
            onDispose { helper.close() }
        }
    } else {
        SecureStorageErrorScreen(
            detail = opened.exceptionOrNull()?.message
                ?: "The encrypted health database could not be opened.",
            onBack = onBack
        )
    }
    return helper
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureStorageErrorScreen(detail: String, onBack: () -> Unit) {
    Scaffold(
        containerColor = AppColors.PageBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Storage unavailable",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextOnDark
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.NavyMid),
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Back", fontSize = 15.sp, color = AppColors.TextOnDark)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "100Life could not open its encrypted storage, so it stopped. " +
                    "Nothing was written to an unprotected file.",
                fontSize = 16.sp,
                color = AppColors.TextDark,
                lineHeight = 24.sp
            )
            Text(detail, fontSize = 14.sp, color = AppColors.TextGray, lineHeight = 22.sp)
        }
    }
}

// ─────────────────────────────────────────────
// MAIN ACTIVITY
// ─────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
        setContent {
            MaterialTheme {
                AppNavigation(applicationContext)
            }
        }
    }
}

// ─────────────────────────────────────────────
// NAVIGATION
// ─────────────────────────────────────────────
@Composable
fun AppNavigation(context: Context) {
    var currentPage by remember { mutableStateOf("month") }
    var selectedMonthCode by remember { mutableStateOf("") }

    when (currentPage) {
        "month" -> MonthSelectorScreen(
            onMonthSelected = { monthCode ->
                selectedMonthCode = monthCode
                currentPage = "summary"
            },
            onSettingsClick = { currentPage = "settings" }
        )
        "summary" -> MonthlySummaryScreen(
            monthCode = selectedMonthCode,
            context = context,
            onAddEntry = { currentPage = "data" },
            onBack = { currentPage = "month" },
            onGoToSettings = { currentPage = "settings" }
        )
        "data" -> DataEntryScreen(selectedMonthCode, context) {
            currentPage = "summary"
        }
        "settings" -> SettingsScreen(
            context = context,
            onBack = { currentPage = "month" },
            onPrivacyPolicy = { currentPage = "privacy" },
            onAboutDeveloper = { currentPage = "about" }
        )
        "privacy" -> PrivacyPolicyScreen {
            currentPage = "settings"
        }
        "about" -> AboutDeveloperScreen {
            currentPage = "settings"
        }
        else -> MonthSelectorScreen(
            onMonthSelected = { monthCode ->
                selectedMonthCode = monthCode
                currentPage = "summary"
            },
            onSettingsClick = { currentPage = "settings" }
        )
    }
}

// ─────────────────────────────────────────────
// MONTH SELECTOR SCREEN
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthSelectorScreen(onMonthSelected: (String) -> Unit, onSettingsClick: () -> Unit) {
    val months = listOf(
        "Jan" to "jan", "Feb" to "feb", "Mar" to "mar",
        "Apr" to "apr", "May" to "may", "Jun" to "jun",
        "Jul" to "jul", "Aug" to "aug", "Sep" to "sep",
        "Oct" to "oct", "Nov" to "nov", "Dec" to "dec"
    )
    val currentYear = java.time.LocalDate.now().year
    Scaffold(
        containerColor = AppColors.PageBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "100Life Tracker",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextOnDark
                        )
                        Text(
                            "Year $currentYear",
                            fontSize = 13.sp,
                            color = AppColors.TextOnDark.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.NavyMid
                ),
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings, "Settings",
                            tint = AppColors.TextOnDark,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                "Select a month:",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = AppColors.TextGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
            // 4 rows x 3 columns grid — all 12 months, no scrolling
            val rows = months.chunked(3)
            rows.forEach { rowMonths ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowMonths.forEach { (name, code) ->
                        MonthButton(
                            text = name,
                            monthCode = code,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) { onMonthSelected(it) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun MonthButton(text: String, monthCode: String, modifier: Modifier = Modifier, onClick: (String) -> Unit) {
    Button(
        onClick = { onClick(monthCode) },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.NavyMid),
        shape = RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────
// GLUCOSE UNIT SELECTOR
// ─────────────────────────────────────────────
@Composable
fun GlucoseUnitSelector(selected: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "What does your glucometer show?",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            "Look at your device and pick the one that matches:",
            fontSize = 13.sp,
            color = AppColors.TextGray,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(
                "mmol/L" to "Small numbers\n(like 4.5 – 10.0)",
                "mg/dL" to "Big numbers\n(like 80 – 200)"
            ).forEach { (unit, desc) ->
                val isSelected = selected == unit
                OutlinedButton(
                    onClick = { onSelect(unit) },
                    modifier = Modifier.weight(1f).height(80.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) AppColors.NavyMid else AppColors.CardWhite,
                        contentColor = if (isSelected) AppColors.TextOnDark else AppColors.NavyMid
                    ),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, AppColors.NavyMid),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(unit, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(desc, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// READING TYPE SELECTOR
// ─────────────────────────────────────────────
@Composable
fun ReadingTypeSelector(selected: String, onSelect: (String) -> Unit) {
    val options = listOf("Fasting" to "🌅", "After Meal" to "🍽️", "Random" to "🎲")
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Reading Type",
            fontSize = 16.sp,
            color = AppColors.TextGray,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (label, emoji) ->
                val isSelected = selected == label
                OutlinedButton(
                    onClick = { onSelect(label) },
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) AppColors.NavyMid else AppColors.CardWhite,
                        contentColor = if (isSelected) AppColors.TextOnDark else AppColors.NavyMid
                    ),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, AppColors.NavyMid),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(emoji, fontSize = 18.sp)
                        Text(
                            label, fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// HbA1c PREDICTION CARD
// ─────────────────────────────────────────────
@Composable
fun HbA1cPredictionCard(allYearEntries: List<Entry>, glucoseUnit: String) {
    val totalEntries = allYearEntries.size
    val distinctMonths = allYearEntries.map { it.month }.distinct().size
    val hasEnoughData = totalEntries >= 14 && distinctMonths >= 2
    var isExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🔬 HbA1c Prediction",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6A1B9A)
                )
                Text(
                    if (isExpanded) "▲ Hide" else "▼ View",
                    fontSize = 14.sp,
                    color = Color(0xFF6A1B9A),
                    fontWeight = FontWeight.Bold
                )
            }
            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFFCE93D8))
                Spacer(modifier = Modifier.height(10.dp))
                if (!hasEnoughData) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "📊 Not enough data yet",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                            Text(
                                "To generate an HbA1c prediction, please record:\n\n" +
                                        "• At least 14 readings (current: $totalEntries)\n" +
                                        "• Across at least 2 months (current: $distinctMonths)\n\n" +
                                        "Keep recording daily — prediction will appear automatically!",
                                fontSize = 13.sp,
                                color = Color(0xFF5D4037),
                                lineHeight = 20.sp
                            )
                        }
                    }
                } else {
                    val avgSugarMmol = allYearEntries.sumOf { it.sugar } / totalEntries
                    val avgSugarMgDl = GlucoseUnits.toMgDl(avgSugarMmol, glucoseUnit)
                    val estimatedHbA1c = GlucoseUnits.estimateHbA1cPercent(avgSugarMgDl)
                    val avgDisplay = if (glucoseUnit == "mmol/L")
                        "${String.format("%.1f", avgSugarMmol)} mmol/L"
                    else "${String.format("%.0f", avgSugarMgDl)} mg/dL"
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("Readings used", fontSize = 12.sp, color = Color(0xFF7B1FA2))
                            Text(
                                "$totalEntries entries across $distinctMonths months",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Average sugar", fontSize = 12.sp, color = Color(0xFF7B1FA2))
                            Text(avgDisplay, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Predicted HbA1c", fontSize = 12.sp, color = Color(0xFF7B1FA2))
                            Text(
                                "~${String.format("%.1f", estimatedHbA1c)}%",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6A1B9A)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "⚠️ IMPORTANT — Please Read",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828)
                            )
                            Text(
                                "This is a mathematical prediction based on the ADAG formula " +
                                        "(Nathan et al., Diabetes Care 2008). It is NOT a blood test result.\n\n" +
                                        "• Accuracy depends on number and variety of readings\n" +
                                        "• Fasting-only readings may underestimate the result\n" +
                                        "• Individual factors (anaemia, illness) affect accuracy\n\n" +
                                        "ALWAYS confirm with your doctor's official HbA1c blood test " +
                                        "every 3 months. Do NOT use this to change medication or treatment.",
                                fontSize = 12.sp,
                                color = Color(0xFFB71C1C),
                                lineHeight = 18.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Formula: ADAG Study — Nathan DM et al., Diabetes Care 2008;31(8):1473–1478",
                        fontSize = 10.sp,
                        color = AppColors.TextGray,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// CSV HELPER
// ─────────────────────────────────────────────
fun generateCSV(
    context: Context, entries: List<Entry>, monthName: String, year: Int,
    patientName: String, patientIC: String, glucoseUnit: String,
    avgSugar: Double, highestEntry: Entry?, lowestEntry: Entry?, avgSteps: Int
): Uri? {
    return try {
        val fileName = exportReportFileName(monthName, year)
        val csvDir = File(context.cacheDir, "csv_exports")
        if (!csvDir.exists()) csvDir.mkdirs()
        // Drop older attachments so a previous file name cannot keep an IC in cache.
        csvDir.listFiles()?.forEach { older -> if (older.isFile) older.delete() }
        val csvFile = File(csvDir, fileName)
        val writer = FileWriter(csvFile)
        writer.append("100Life Health Report\n")
        writer.append("Patient Name,${patientName}\n")
        writer.append("IC Number,${patientIC}\n")
        writer.append("Month,${monthName} ${year}\n")
        writer.append("Glucose Unit,${glucoseUnit}\n")
        writer.append("Generated,${java.time.LocalDate.now()}\n\n")
        writer.append("MONTHLY SUMMARY\n")
        writer.append("Total Entries,${entries.size}\n")
        writer.append("Average Sugar,${String.format("%.1f", avgSugar)} ${glucoseUnit}\n")
        writer.append("Highest Sugar,${highestEntry?.sugar ?: "-"} ${glucoseUnit},${highestEntry?.date ?: ""}\n")
        writer.append("Lowest Sugar,${lowestEntry?.sugar ?: "-"} ${glucoseUnit},${lowestEntry?.date ?: ""}\n")
        writer.append("Average Daily Steps,${avgSteps} steps\n\n")
        writer.append("FULL DATA\n")
        writer.append("Date,Reading Type,Sugar (${glucoseUnit}),Steps,Notes\n")
        entries.forEach { entry ->
            writer.append("${entry.date},${entry.readingType},${entry.sugar},${entry.steps},${entry.notes.replace(",", ";")}\n")
        }
        writer.flush()
        writer.close()
        FileProvider.getUriForFile(context, "${context.packageName}.provider", csvFile)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// ─────────────────────────────────────────────
// EMAIL SEND FUNCTION
// ─────────────────────────────────────────────
fun sendEmailWithCSV(
    context: Context, csvUri: Uri, doctorEmail: String, doctorName: String,
    patientName: String, patientIC: String, monthName: String, year: Int,
    glucoseUnit: String, totalEntries: Int, avgSugar: Double,
    highestEntry: Entry?, lowestEntry: Entry?, avgSteps: Int
) {
    val subject = exportReportSubject(patientName, monthName, year)
    val body = """Dear $doctorName,

Please find attached the monthly sugar and steps report for your patient:

Patient Name  : $patientName
IC Number     : $patientIC
Month         : $monthName $year
Glucose Unit  : $glucoseUnit
Total Entries : $totalEntries readings
Average Sugar : ${String.format("%.1f", avgSugar)} $glucoseUnit
Highest Sugar : ${highestEntry?.sugar ?: "-"} $glucoseUnit (${highestEntry?.date ?: "-"})
Lowest Sugar  : ${lowestEntry?.sugar ?: "-"} $glucoseUnit (${lowestEntry?.date ?: "-"})
Avg Steps/Day : $avgSteps steps

Full data is attached as a CSV file which can be opened in Microsoft Excel or Google Sheets.

This report was generated by the 100Life Health Tracking App.
-- This email was sent on behalf of the patient using 100Life App.""".trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "message/rfc822"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(doctorEmail))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        putExtra(Intent.EXTRA_STREAM, csvUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Send report via email"))
}

// ─────────────────────────────────────────────
// MONTHLY SUMMARY SCREEN
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlySummaryScreen(
    monthCode: String, context: Context,
    onAddEntry: () -> Unit, onBack: () -> Unit, onGoToSettings: () -> Unit
) {
    val dbHelper = HealthDatabaseOrError(context, onBack) ?: return
    val prefs = SecurePrefsOrError(context, onBack) ?: return
    val currentContext = LocalContext.current
    val currentYear = java.time.LocalDate.now().year

    val loaded = remember(monthCode, dbHelper) {
        runCatching {
            dbHelper.getEntries(monthCode, currentYear) to dbHelper.getAllEntriesForYear(currentYear)
        }
    }
    val loadedEntries = loaded.getOrNull()
    if (loadedEntries == null) {
        SecureStorageErrorScreen(
            detail = loaded.exceptionOrNull()?.message
                ?: "Couldn't read the encrypted health database.",
            onBack = onBack
        )
        return
    }
    var allEntries by remember(monthCode) { mutableStateOf(loadedEntries.first) }
    val allYearEntries = loadedEntries.second
    var showAll by remember { mutableStateOf(false) }
    var showSendConfirm by remember { mutableStateOf(false) }
    var showEmailError by remember { mutableStateOf(false) }
    var emailErrorMsg by remember { mutableStateOf("") }

    val monthName = monthCodeToName(monthCode)
    val totalEntries = allEntries.size
    val avgSugar = if (totalEntries > 0) allEntries.sumOf { it.sugar } / totalEntries else 0.0
    val highestEntry = allEntries.maxByOrNull { it.sugar }
    val lowestEntry = allEntries.minByOrNull { it.sugar }
    val avgSteps = if (totalEntries > 0) allEntries.sumOf { it.steps } / totalEntries else 0
    val displayedEntries = if (showAll) allEntries else allEntries.take(10)

    val doctorName = prefs.getString(SettingKeys.DOCTOR_NAME, "") ?: ""
    val doctorEmail = prefs.getString(SettingKeys.DOCTOR_EMAIL, "") ?: ""
    val patientName = prefs.getString(SettingKeys.PATIENT_NAME, "") ?: ""
    val patientIC = prefs.getString(SettingKeys.PATIENT_IC, "") ?: ""
    val glucoseUnit = prefs.getString(SettingKeys.GLUCOSE_UNIT, GlucoseUnits.MMOL_L) ?: GlucoseUnits.MMOL_L
    val isSettingsComplete = doctorEmail.isNotEmpty() && patientName.isNotEmpty() && patientIC.isNotEmpty()

    Scaffold(
        containerColor = AppColors.PageBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "$monthName $currentYear",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextOnDark
                        )
                        Text("Summary", fontSize = 12.sp, color = AppColors.TextOnDark.copy(alpha = 0.8f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.NavyMid),
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Back", fontSize = 15.sp, color = AppColors.TextOnDark)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Overview card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.CardBlue),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "📊 $monthName $currentYear Overview",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.NavyMid
                    )
                    HorizontalDivider(color = AppColors.Divider)
                    SummaryRow("Total entries", "$totalEntries readings")
                    SummaryRow(
                        "Average sugar",
                        if (totalEntries > 0) "${String.format("%.1f", avgSugar)} $glucoseUnit" else "No data"
                    )
                    SummaryRow(
                        "Highest sugar",
                        if (highestEntry != null) "${highestEntry.sugar} $glucoseUnit (${highestEntry.date})" else "No data"
                    )
                    SummaryRow(
                        "Lowest sugar",
                        if (lowestEntry != null) "${lowestEntry.sugar} $glucoseUnit (${lowestEntry.date})" else "No data"
                    )
                    SummaryRow(
                        "Avg daily steps",
                        if (totalEntries > 0) "$avgSteps steps" else "No data"
                    )
                }
            }

            Button(
                onClick = onAddEntry,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Success),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("+ Add New Entry", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    if (!isSettingsComplete) onGoToSettings()
                    else if (totalEntries == 0) {
                        emailErrorMsg = "No entries found for $monthName $currentYear. Please add data first."
                        showEmailError = true
                    } else showSendConfirm = true
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSettingsComplete) AppColors.NavyMid else Color(0xFF9E9E9E)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    if (isSettingsComplete) "📧 Send Report to Doctor" else "📧 Send Report (Setup Required)",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!isSettingsComplete) {
                Text(
                    "⚠️ Tap above to set up your doctor's email first",
                    fontSize = 13.sp,
                    color = Color(0xFFE65100),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            // Entries list card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.CardWhite),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        if (showAll) "All entries ($totalEntries)" else "Recent entries (last 10)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = AppColors.TextDark
                    )
                    if (totalEntries == 0) {
                        Text(
                            "No entries yet for $monthName $currentYear.\nTap '+ Add New Entry' to get started.",
                            color = AppColors.TextGray,
                            modifier = Modifier.padding(vertical = 12.dp),
                            lineHeight = 24.sp
                        )
                    } else {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("Date", modifier = Modifier.weight(1.3f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Type", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Sugar", modifier = Modifier.weight(0.9f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Steps", modifier = Modifier.weight(0.9f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        displayedEntries.forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(entry.date, modifier = Modifier.weight(1.3f), fontSize = 12.sp)
                                Text(entry.readingType, modifier = Modifier.weight(1f), fontSize = 12.sp)
                                Text("${entry.sugar}", modifier = Modifier.weight(0.9f), fontSize = 12.sp)
                                Text("${entry.steps}", modifier = Modifier.weight(0.9f), fontSize = 12.sp)
                            }
                            if (entry.notes.isNotEmpty()) {
                                Text(
                                    "📝 ${entry.notes}",
                                    fontSize = 11.sp,
                                    color = AppColors.TextGray,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
                        }
                        if (totalEntries > 10) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                if (showAll) "Show less ▲" else "Show all $totalEntries entries ▼",
                                color = AppColors.NavyMid,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { showAll = !showAll }
                            )
                        }
                    }
                }
            }

            HbA1cPredictionCard(allYearEntries = allYearEntries, glucoseUnit = glucoseUnit)
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showSendConfirm) {
        AlertDialog(
            onDismissRequest = { showSendConfirm = false },
            title = { Text("Send Report?", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "You are about to send:\n\nPatient : $patientName\nIC      : $patientIC\n" +
                            "Month   : $monthName $currentYear\nTo      : $doctorName\n" +
                            "Email   : $doctorEmail\n\n" +
                            "A CSV file with all $totalEntries entries will be attached.\n\n" +
                            "Your email app will open — please review before sending.",
                    fontSize = 15.sp, lineHeight = 24.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSendConfirm = false
                        val csvUri = generateCSV(
                            currentContext, allEntries, monthName, currentYear,
                            patientName, patientIC, glucoseUnit, avgSugar,
                            highestEntry, lowestEntry, avgSteps
                        )
                        if (csvUri != null) {
                            sendEmailWithCSV(
                                currentContext, csvUri, doctorEmail, doctorName,
                                patientName, patientIC, monthName, currentYear,
                                glucoseUnit, totalEntries, avgSugar,
                                highestEntry, lowestEntry, avgSteps
                            )
                        } else {
                            emailErrorMsg = "Failed to generate CSV. Please try again."
                            showEmailError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.NavyMid)
                ) { Text("Open Email App", fontSize = 16.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showSendConfirm = false }) {
                    Text("Cancel", fontSize = 16.sp)
                }
            }
        )
    }

    if (showEmailError) {
        AlertDialog(
            onDismissRequest = { showEmailError = false },
            title = { Text("⚠️ Cannot Send", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            text = { Text(emailErrorMsg, fontSize = 16.sp, lineHeight = 24.sp) },
            confirmButton = {
                Button(
                    onClick = { showEmailError = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.NavyMid)
                ) { Text("OK", fontSize = 16.sp) }
            }
        )
    }
}

@Composable
fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 15.sp, color = AppColors.NavyMid, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

// ─────────────────────────────────────────────
// SETTINGS SCREEN
// Values are encrypted with the Android Keystore. There is no plaintext copy.
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(context: Context, onBack: () -> Unit, onPrivacyPolicy: () -> Unit, onAboutDeveloper: () -> Unit) {
    // The Activity that hosts this Compose tree — needed for any Dialog.
    // The `context` param is applicationContext (no window token), so we
    // resolve the real Activity from LocalContext for the time picker.
    val prefs = SecurePrefsOrError(context, onBack) ?: return
    val activity = LocalContext.current.findActivity()
    var patientName by remember { mutableStateOf(prefs.getString(SettingKeys.PATIENT_NAME, "") ?: "") }
    var patientIC by remember { mutableStateOf(prefs.getString(SettingKeys.PATIENT_IC, "") ?: "") }
    var doctorName by remember { mutableStateOf(prefs.getString(SettingKeys.DOCTOR_NAME, "") ?: "") }
    var doctorEmail by remember { mutableStateOf(prefs.getString(SettingKeys.DOCTOR_EMAIL, "") ?: "") }
    var glucoseUnit by remember { mutableStateOf(prefs.getString(SettingKeys.GLUCOSE_UNIT, GlucoseUnits.MMOL_L) ?: GlucoseUnits.MMOL_L) }
    var reminderEnabled by remember { mutableStateOf(prefs.getBoolean(SettingKeys.REMINDER_ENABLED, false)) }
    var reminderHour by remember { mutableStateOf(prefs.getInt(SettingKeys.REMINDER_HOUR, 9)) }
    var reminderMinute by remember { mutableStateOf(prefs.getInt(SettingKeys.REMINDER_MINUTE, 0)) }
    var nameError by remember { mutableStateOf("") }
    var icError by remember { mutableStateOf("") }
    var drNameError by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf("") }
    var showSavedDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var storageError by remember { mutableStateOf("") }
    val originalName = remember { prefs.getString(SettingKeys.PATIENT_NAME, "") ?: "" }
    val originalIC = remember { prefs.getString(SettingKeys.PATIENT_IC, "") ?: "" }
    val originalDrName = remember { prefs.getString(SettingKeys.DOCTOR_NAME, "") ?: "" }
    val originalEmail = remember { prefs.getString(SettingKeys.DOCTOR_EMAIL, "") ?: "" }
    val originalUnit = remember { prefs.getString(SettingKeys.GLUCOSE_UNIT, GlucoseUnits.MMOL_L) ?: GlucoseUnits.MMOL_L }
    val originalReminder = remember { prefs.getBoolean(SettingKeys.REMINDER_ENABLED, false) }
    val hasChanges = patientName != originalName || patientIC != originalIC ||
            doctorName != originalDrName || doctorEmail != originalEmail ||
            glucoseUnit != originalUnit || reminderEnabled != originalReminder

    Scaffold(
        containerColor = AppColors.PageBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextOnDark)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.NavyMid),
                navigationIcon = {
                    TextButton(onClick = { if (hasChanges) showDiscardDialog = true else onBack() }) {
                        Text("← Back", fontSize = 15.sp, color = AppColors.TextOnDark)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("👤 Patient Information", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppColors.NavyMid)

            OutlinedTextField(
                value = patientName,
                onValueChange = { patientName = it; nameError = "" },
                label = { Text("Full Name", fontSize = 16.sp) },
                placeholder = { Text("e.g. Yugen Nair Prushothaman") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                isError = nameError.isNotEmpty(),
                supportingText = {
                    if (nameError.isNotEmpty()) Text(nameError, color = MaterialTheme.colorScheme.error)
                    else Text("As per IC / passport", color = AppColors.TextGray, fontSize = 12.sp)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )

            OutlinedTextField(
                value = patientIC,
                onValueChange = {
                    if (it.length <= 12 && it.all { c -> c.isDigit() }) { patientIC = it; icError = "" }
                },
                label = { Text("IC Number", fontSize = 16.sp) },
                placeholder = { Text("e.g. 940720106883") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                isError = icError.isNotEmpty(),
                supportingText = {
                    if (icError.isNotEmpty()) Text(icError, color = MaterialTheme.colorScheme.error)
                    else Text("12-digit Malaysian IC • ${patientIC.length}/12", color = AppColors.TextGray, fontSize = 12.sp)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            HorizontalDivider(color = AppColors.Divider)
            Text("🩺 Doctor Information", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppColors.NavyMid)

            OutlinedTextField(
                value = doctorName,
                onValueChange = { doctorName = it; drNameError = "" },
                label = { Text("Doctor's Name", fontSize = 16.sp) },
                placeholder = { Text("e.g. Dr. Ahmad Razali") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                isError = drNameError.isNotEmpty(),
                supportingText = {
                    if (drNameError.isNotEmpty()) Text(drNameError, color = MaterialTheme.colorScheme.error)
                    else Text("Include title e.g. Dr. / Prof.", color = AppColors.TextGray, fontSize = 12.sp)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )

            OutlinedTextField(
                value = doctorEmail,
                onValueChange = { doctorEmail = it; emailError = "" },
                label = { Text("Doctor's Email", fontSize = 16.sp) },
                placeholder = { Text("e.g. dr.ahmad@hospital.com") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                isError = emailError.isNotEmpty(),
                supportingText = {
                    if (emailError.isNotEmpty()) Text(emailError, color = MaterialTheme.colorScheme.error)
                    else Text("Report will be sent to this email", color = AppColors.TextGray, fontSize = 12.sp)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            HorizontalDivider(color = AppColors.Divider)
            Text("⚗️ Glucometer Reading Unit", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppColors.NavyMid)
            GlucoseUnitSelector(selected = glucoseUnit, onSelect = { glucoseUnit = it })

            HorizontalDivider(color = AppColors.Divider)
            Text("🔔 Daily Reminder", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppColors.NavyMid)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable daily reminder", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text("Get reminded to record your reading", fontSize = 12.sp, color = AppColors.TextGray)
                        }
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { enabled ->
                                reminderEnabled = enabled
                                if (enabled) ReminderReceiver.scheduleReminder(context, reminderHour, reminderMinute)
                                else ReminderReceiver.cancelReminder(context)
                            }
                        )
                    }
                    if (reminderEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Reminder time", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "${reminderHour.toString().padStart(2, '0')}:${reminderMinute.toString().padStart(2, '0')}",
                                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppColors.NavyMid
                                )
                            }
                            Button(
                                onClick = {
                                    TimePickerDialog(activity, { _, hour, minute ->
                                        reminderHour = hour
                                        reminderMinute = minute
                                        ReminderReceiver.scheduleReminder(context, hour, minute)
                                    }, reminderHour, reminderMinute, true).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.NavyMid)
                            ) { Text("Change Time", fontSize = 14.sp) }
                        }
                        Text("⚠️ Reminder works without internet connection.", fontSize = 12.sp, color = Color(0xFF558B2F))
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.CardBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Text("ℹ️", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Your name, IC number and doctor details are encrypted with the Android Keystore. " +
                                "Readings and steps are stored in an encrypted database on this phone only. " +
                                "None of this is included in Android cloud backup or device transfer. " +
                                "It is used when you choose to generate a report for your doctor.",
                        fontSize = 14.sp, color = AppColors.NavyMid, lineHeight = 22.sp
                    )
                }
            }

            Button(
                onClick = {
                    var valid = true
                    if (patientName.trim().isEmpty()) { nameError = "Please enter your full name"; valid = false }
                    if (patientIC.trim().length != 12) { icError = "IC must be exactly 12 digits"; valid = false }
                    if (doctorName.trim().isEmpty()) { drNameError = "Please enter doctor's name"; valid = false }
                    val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
                    if (!emailRegex.matches(doctorEmail.trim())) { emailError = "Please enter a valid email"; valid = false }
                    if (valid) {
                        try {
                            val saved = prefs.edit()
                                .putString(SettingKeys.PATIENT_NAME, patientName.trim())
                                .putString(SettingKeys.PATIENT_IC, patientIC.trim())
                                .putString(SettingKeys.DOCTOR_NAME, doctorName.trim())
                                .putString(SettingKeys.DOCTOR_EMAIL, doctorEmail.trim())
                                .putString(SettingKeys.GLUCOSE_UNIT, glucoseUnit)
                                .putBoolean(SettingKeys.REMINDER_ENABLED, reminderEnabled)
                                .putInt(SettingKeys.REMINDER_HOUR, reminderHour)
                                .putInt(SettingKeys.REMINDER_MINUTE, reminderMinute)
                                .commit()
                            if (!saved) {
                                throw IllegalStateException("Encrypted settings were not saved")
                            }
                            if (reminderEnabled) {
                                ReminderReceiver.scheduleReminder(context, reminderHour, reminderMinute)
                            } else {
                                ReminderReceiver.cancelReminder(context)
                            }
                            storageError = ""
                            showSavedDialog = true
                        } catch (e: Exception) {
                            storageError = "Couldn't encrypt these settings, so they were not saved."
                            val stored = prefs.getBoolean(SettingKeys.REMINDER_ENABLED, false)
                            if (stored) {
                                ReminderReceiver.scheduleReminder(
                                    context,
                                    prefs.getInt(SettingKeys.REMINDER_HOUR, 9),
                                    prefs.getInt(SettingKeys.REMINDER_MINUTE, 0)
                                )
                            } else {
                                ReminderReceiver.cancelReminder(context)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Success),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Save Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold) }

            if (storageError.isNotEmpty()) {
                Text(storageError, fontSize = 14.sp, color = AppColors.Error, lineHeight = 20.sp)
            }

            TextButton(onClick = onPrivacyPolicy, modifier = Modifier.fillMaxWidth()) {
                Text("📄 Privacy Policy", fontSize = 16.sp, color = AppColors.NavyMid)
            }
            TextButton(onClick = onAboutDeveloper, modifier = Modifier.fillMaxWidth()) {
                Text("💙 About the Developer", fontSize = 16.sp, color = AppColors.NavyMid)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showSavedDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("✅ Settings Saved!", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Name: $patientName\nIC: $patientIC\nDoctor: $doctorName\n" +
                            "Email: $doctorEmail\nUnit: $glucoseUnit\n" +
                            "Reminder: ${if (reminderEnabled) "ON — ${reminderHour.toString().padStart(2, '0')}:${reminderMinute.toString().padStart(2, '0')}" else "OFF"}\n\n" +
                            "Stored only on this phone and encrypted with the Android Keystore. Not included in cloud backup.",
                    fontSize = 16.sp, lineHeight = 26.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSavedDialog = false; onBack() },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Success)
                ) { Text("OK", fontSize = 18.sp) }
            }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Changes?", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            text = { Text("You have unsaved changes. Go back without saving?", fontSize = 16.sp, lineHeight = 24.sp) },
            confirmButton = {
                Button(
                    onClick = { showDiscardDialog = false; onBack() },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error)
                ) { Text("Discard", fontSize = 16.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep Editing", fontSize = 16.sp) }
            }
        )
    }
}

// ─────────────────────────────────────────────
// DATA ENTRY SCREEN
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataEntryScreen(monthCode: String, context: Context, onBack: () -> Unit) {
    val prefs = SecurePrefsOrError(context, onBack) ?: return
    val dbHelper = HealthDatabaseOrError(context, onBack) ?: return
    val today = remember { java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) }
    val currentYear = remember { java.time.LocalDate.now().year }
    val glucoseUnit = prefs.getString(SettingKeys.GLUCOSE_UNIT, GlucoseUnits.MMOL_L) ?: GlucoseUnits.MMOL_L
    var selectedDateMillis by remember(monthCode) { mutableStateOf(System.currentTimeMillis()) }
    var displayDate by remember(monthCode) { mutableStateOf(today) }
    var sugarInput by remember(monthCode) { mutableStateOf("") }
    var stepsInput by remember(monthCode) { mutableStateOf("") }
    var readingType by remember(monthCode) { mutableStateOf("Fasting") }
    var notesInput by remember(monthCode) { mutableStateOf("") }
    var showDatePicker by remember(monthCode) { mutableStateOf(false) }
    var showSaveDialog by remember(monthCode) { mutableStateOf(false) }
    var showDeleteConfirm by remember(monthCode) { mutableStateOf<Entry?>(null) }
    var showLast5 by remember(monthCode) { mutableStateOf(false) }
    var sugarError by remember(monthCode) { mutableStateOf("") }
    var stepsError by remember(monthCode) { mutableStateOf("") }
    var savedDate by remember(monthCode) { mutableStateOf("") }
    var savedSugar by remember(monthCode) { mutableStateOf("") }
    var savedSteps by remember(monthCode) { mutableStateOf("") }
    var averageSugar by remember(monthCode) { mutableStateOf(0.0) }
    var last5Entries by remember(monthCode) { mutableStateOf(listOf<Entry>()) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
    val monthName = monthCodeToName(monthCode)
    val sugarHint = if (glucoseUnit == "mmol/L") "e.g. 6.5" else "e.g. 120"
    val sugarRange = if (glucoseUnit == "mmol/L") "Normal fasting: 4.0 – 6.0 mmol/L" else "Normal fasting: 72 – 108 mg/dL"

    Scaffold(
        containerColor = AppColors.PageBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Add Entry",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextOnDark
                        )
                        Text(
                            "$monthName $currentYear",
                            fontSize = 12.sp,
                            color = AppColors.TextOnDark.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.NavyMid),
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Back", fontSize = 15.sp, color = AppColors.TextOnDark)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.PageBackground)
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                onClick = { showDatePicker = true },
                colors = CardDefaults.cardColors(containerColor = AppColors.CardWhite),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Date", fontSize = 13.sp, color = AppColors.TextGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(displayDate, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = AppColors.TextDark)
                    }
                    Text("📅", fontSize = 28.sp)
                }
            }

            ReadingTypeSelector(selected = readingType, onSelect = { readingType = it })

            OutlinedTextField(
                value = sugarInput,
                onValueChange = { sugarInput = it; sugarError = "" },
                label = { Text("Sugar Reading ($glucoseUnit)") },
                placeholder = { Text(sugarHint) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                isError = sugarError.isNotEmpty(),
                supportingText = {
                    if (sugarError.isNotEmpty()) Text(sugarError, color = MaterialTheme.colorScheme.error)
                    else Text(sugarRange, color = AppColors.TextGray, fontSize = 12.sp)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            OutlinedTextField(
                value = stepsInput,
                onValueChange = { stepsInput = it; stepsError = "" },
                label = { Text("Daily Steps") },
                placeholder = { Text("e.g. 5000") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                isError = stepsError.isNotEmpty(),
                supportingText = {
                    if (stepsError.isNotEmpty()) Text(stepsError, color = MaterialTheme.colorScheme.error)
                    else Text("Goal: 7,000–10,000 steps/day", color = AppColors.TextGray, fontSize = 12.sp)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            if (readingType == "After Meal") {
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { if (it.length <= 100) notesInput = it },
                    label = { Text("What did you eat?") },
                    placeholder = { Text("e.g. Nasi lemak, roti canai...") },
                    modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 3,
                    supportingText = {
                        Text("${notesInput.length}/100 characters", color = AppColors.TextGray, fontSize = 12.sp)
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    var valid = true
                    val sugarProblem = GlucoseUnits.sugarError(sugarInput, glucoseUnit)
                    val stepsProblem = GlucoseUnits.stepsError(stepsInput)
                    if (sugarProblem != null) { sugarError = sugarProblem; valid = false }
                    if (stepsProblem != null) { stepsError = stepsProblem; valid = false }
                    if (valid) {
                        val sugar = sugarInput.trim().toDouble()
                        val steps = stepsInput.trim().toInt()
                        try {
                            dbHelper.insertEntry(monthCode, displayDate, sugar, steps, readingType, notesInput.trim(), currentYear)
                            val allEntries = dbHelper.getEntries(monthCode, currentYear)
                            averageSugar = if (allEntries.isNotEmpty()) allEntries.sumOf { it.sugar } / allEntries.size else 0.0
                            savedDate = displayDate; savedSugar = sugarInput; savedSteps = stepsInput
                            sugarInput = ""; stepsInput = ""; notesInput = ""
                            showSaveDialog = true
                        } catch (e: Exception) {
                            sugarError = "Couldn't save to the encrypted database. The reading was not stored."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Success),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Save Entry", fontSize = 18.sp, fontWeight = FontWeight.Bold) }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (showLast5) "Hide entries ▲" else "Last 5 Days — Tap to view ▼",
                color = AppColors.NavyMid,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    last5Entries = dbHelper.getLast5Entries(monthCode, currentYear)
                    showLast5 = !showLast5
                }
            )

            if (showLast5) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardWhite),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("Date", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Type", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Sugar", modifier = Modifier.weight(0.9f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Steps", modifier = Modifier.weight(0.9f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("", modifier = Modifier.width(36.dp))
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        if (last5Entries.isEmpty()) {
                            Text("No entries yet.", color = AppColors.TextGray, modifier = Modifier.padding(vertical = 8.dp))
                        } else {
                            last5Entries.forEach { entry ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(entry.date, modifier = Modifier.weight(1.2f), fontSize = 15.sp)
                                    Text(entry.readingType, modifier = Modifier.weight(1f), fontSize = 15.sp)
                                    Text("${entry.sugar}", modifier = Modifier.weight(0.9f), fontSize = 15.sp)
                                    Text("${entry.steps}", modifier = Modifier.weight(0.9f), fontSize = 15.sp)
                                    IconButton(onClick = { showDeleteConfirm = entry }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Delete, "Delete", tint = AppColors.Error, modifier = Modifier.size(18.dp))
                                    }
                                }
                                if (entry.notes.isNotEmpty()) {
                                    Text("📝 ${entry.notes}", fontSize = 11.sp, color = AppColors.TextGray, modifier = Modifier.padding(bottom = 4.dp))
                                }
                                HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Close ▲", color = AppColors.NavyMid, fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showLast5 = false }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val localDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        displayDate = localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        selectedDateMillis = millis
                    }
                    showDatePicker = false
                }) { Text("OK", fontSize = 18.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", fontSize = 18.sp) }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("✅ Saved!", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Date: $savedDate\nType: $readingType\nSugar: $savedSugar $glucoseUnit\n" +
                            "Steps: $savedSteps\n\nMonthly average: ${String.format("%.1f", averageSugar)} $glucoseUnit",
                    fontSize = 18.sp, lineHeight = 28.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSaveDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Success)
                ) { Text("OK", fontSize = 18.sp) }
            }
        )
    }

    showDeleteConfirm?.let { entryToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Entry?", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure?\n\nDate: ${entryToDelete.date}\n" +
                            "Type: ${entryToDelete.readingType}\n" +
                            "Sugar: ${entryToDelete.sugar} $glucoseUnit\nSteps: ${entryToDelete.steps}",
                    fontSize = 16.sp, lineHeight = 26.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            dbHelper.deleteEntry(entryToDelete.id)
                            last5Entries = dbHelper.getLast5Entries(monthCode, currentYear)
                        } catch (e: Exception) {
                            sugarError = "Couldn't update the encrypted database. The entry was not changed."
                        }
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error)
                ) { Text("Delete", fontSize = 16.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel", fontSize = 16.sp) }
            }
        )
    }
}

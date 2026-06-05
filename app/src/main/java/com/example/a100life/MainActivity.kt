package com.example.a100life

import android.content.Context
import android.content.ContentValues
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ─────────────────────────────────────────────
// MAIN ACTIVITY
// ─────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    var currentPage by remember { mutableStateOf("welcome") }
    var selectedMonthCode by remember { mutableStateOf("") }
    var isFirstTime by remember { mutableStateOf(true) }

    if (isFirstTime) {
        WelcomeScreen(onContinue = {
            isFirstTime = false
            currentPage = "month"
        })
    } else {
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
            "settings" -> SettingsScreen(context) {
                currentPage = "month"
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
}

// ─────────────────────────────────────────────
// WELCOME SCREEN
// ─────────────────────────────────────────────

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to 100Life!", fontSize = 32.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp), textAlign = TextAlign.Center)
        Text("This app helps you track:\n\n• Sugar readings (morning)\n• Daily steps\n• Date\n\nYour data is saved safely on your phone.",
            fontSize = 22.sp, lineHeight = 32.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(40.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(72.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
            Text("Get Started", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────
// MONTH SELECTOR SCREEN
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthSelectorScreen(onMonthSelected: (String) -> Unit, onSettingsClick: () -> Unit) {
    val months = listOf(
        "January" to "jan", "February" to "feb", "March" to "mar",
        "April" to "apr", "May" to "may", "June" to "jun",
        "July" to "jul", "August" to "aug", "September" to "sep",
        "October" to "oct", "November" to "nov", "December" to "dec"
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("100Life Tracker", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, "Settings",
                            tint = Color(0xFF2196F3), modifier = Modifier.size(28.dp))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(paddingValues).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Select a month to record or view entries:", fontSize = 18.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 20.dp, top = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                months.forEach { (name, code) -> MonthButton(name, code) { onMonthSelected(it) } }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun MonthButton(text: String, monthCode: String, onClick: (String) -> Unit) {
    Button(onClick = { onClick(monthCode) }, modifier = Modifier.fillMaxWidth().height(60.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))) {
        Text(text = text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────
// MONTHLY SUMMARY SCREEN (NEW)
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlySummaryScreen(
    monthCode: String,
    context: Context,
    onAddEntry: () -> Unit,
    onBack: () -> Unit,
    onGoToSettings: () -> Unit
) {
    val dbHelper = remember { DatabaseHelper(context) }
    val prefs = context.getSharedPreferences("100life_settings", Context.MODE_PRIVATE)

    val allEntries = remember(monthCode) { dbHelper.getEntries(monthCode) }
    var showAll by remember { mutableStateOf(false) }
    var showSendConfirm by remember { mutableStateOf(false) }

    val monthName = mapOf(
        "jan" to "January", "feb" to "February", "mar" to "March",
        "apr" to "April", "may" to "May", "jun" to "June",
        "jul" to "July", "aug" to "August", "sep" to "September",
        "oct" to "October", "nov" to "November", "dec" to "December"
    )[monthCode] ?: monthCode

    // ── Compute summary stats ──
    val totalEntries = allEntries.size
    val avgSugar = if (allEntries.isNotEmpty()) allEntries.sumOf { it.sugar } / totalEntries else 0.0
    val highestEntry = allEntries.maxByOrNull { it.sugar }
    val lowestEntry = allEntries.minByOrNull { it.sugar }
    val avgSteps = if (allEntries.isNotEmpty()) allEntries.sumOf { it.steps } / totalEntries else 0

    val displayedEntries = if (showAll) allEntries else allEntries.take(10)

    // ── Doctor info from settings ──
    val doctorName  = prefs.getString("doctor_name",  "") ?: ""
    val doctorEmail = prefs.getString("doctor_email", "") ?: ""
    val patientName = prefs.getString("patient_name", "") ?: ""
    val patientIC   = prefs.getString("patient_ic",   "") ?: ""
    val isSettingsComplete = doctorEmail.isNotEmpty() && patientName.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$monthName Summary", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Back", fontSize = 16.sp, color = Color(0xFF2196F3))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(paddingValues).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Stats Card ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📊 $monthName Overview", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFF1565C0))
                    Divider(color = Color(0xFF90CAF9))

                    SummaryRow("Total entries", "$totalEntries readings")
                    SummaryRow("Average sugar", if (totalEntries > 0) "${String.format("%.1f", avgSugar)} mg/dL" else "No data")
                    SummaryRow("Highest sugar",
                        if (highestEntry != null) "${highestEntry.sugar} mg/dL  (${highestEntry.date})" else "No data")
                    SummaryRow("Lowest sugar",
                        if (lowestEntry != null) "${lowestEntry.sugar} mg/dL  (${lowestEntry.date})" else "No data")
                    SummaryRow("Avg daily steps", if (totalEntries > 0) "$avgSteps steps" else "No data")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Action Buttons ──
            Button(
                onClick = onAddEntry,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("+ Add New Entry", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    if (!isSettingsComplete) {
                        onGoToSettings()
                    } else {
                        showSendConfirm = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSettingsComplete) Color(0xFF1565C0) else Color(0xFF9E9E9E)
                )
            ) {
                Text(
                    text = if (isSettingsComplete) "📧 Send Report to Doctor" else "📧 Send Report (Setup Required)",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
            }

            if (!isSettingsComplete) {
                Text(
                    text = "⚠️ Tap above to set up your doctor's email first",
                    fontSize = 13.sp,
                    color = Color(0xFFE65100),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Entries Table ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {

                    Text(
                        text = if (showAll) "All entries ($totalEntries)" else "Recent entries (last 10)",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (totalEntries == 0) {
                        Text("No entries yet for $monthName.", color = Color.Gray,
                            modifier = Modifier.padding(vertical = 12.dp))
                    } else {
                        // Table header
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("Date",  modifier = Modifier.weight(1.4f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Sugar", modifier = Modifier.weight(1f),   fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Steps", modifier = Modifier.weight(1f),   fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Divider(modifier = Modifier.padding(vertical = 6.dp))

                        displayedEntries.forEach { entry ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Text(entry.date,             modifier = Modifier.weight(1.4f), fontSize = 13.sp)
                                Text("${entry.sugar} mg/dL", modifier = Modifier.weight(1f),   fontSize = 13.sp)
                                Text("${entry.steps}",       modifier = Modifier.weight(1f),   fontSize = 13.sp)
                            }
                            Divider(color = Color.LightGray, thickness = 0.5.dp)
                        }

                        // Show all / collapse toggle
                        if (totalEntries > 10) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (showAll) "Show less ▲" else "Show all $totalEntries entries ▼",
                                color = Color(0xFF1565C0),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { showAll = !showAll }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ── Send Confirmation Dialog ──
    if (showSendConfirm) {
        AlertDialog(
            onDismissRequest = { showSendConfirm = false },
            title = { Text("Send Report?", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "You are about to send:\n\n" +
                            "Patient : $patientName\n" +
                            "IC      : $patientIC\n" +
                            "Month   : $monthName\n" +
                            "To      : $doctorName\n" +
                            "Email   : $doctorEmail\n\n" +
                            "A CSV file with all $totalEntries entries will be attached.",
                    fontSize = 15.sp, lineHeight = 24.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSendConfirm = false
                        // Email sending — coming next!
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                ) { Text("Send Now", fontSize = 16.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showSendConfirm = false }) {
                    Text("Cancel", fontSize = 16.sp)
                }
            }
        )
    }
}

// ── Helper composable for summary rows ──
@Composable
fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 15.sp, color = Color(0xFF1565C0), modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

// ─────────────────────────────────────────────
// SETTINGS SCREEN
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(context: Context, onBack: () -> Unit) {
    val prefs: SharedPreferences = context.getSharedPreferences("100life_settings", Context.MODE_PRIVATE)

    var patientName  by remember { mutableStateOf(prefs.getString("patient_name",  "") ?: "") }
    var patientIC    by remember { mutableStateOf(prefs.getString("patient_ic",    "") ?: "") }
    var doctorName   by remember { mutableStateOf(prefs.getString("doctor_name",   "") ?: "") }
    var doctorEmail  by remember { mutableStateOf(prefs.getString("doctor_email",  "") ?: "") }

    var nameError   by remember { mutableStateOf("") }
    var icError     by remember { mutableStateOf("") }
    var drNameError by remember { mutableStateOf("") }
    var emailError  by remember { mutableStateOf("") }

    var showSavedDialog   by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val originalName   = remember { prefs.getString("patient_name",  "") ?: "" }
    val originalIC     = remember { prefs.getString("patient_ic",    "") ?: "" }
    val originalDrName = remember { prefs.getString("doctor_name",   "") ?: "" }
    val originalEmail  = remember { prefs.getString("doctor_email",  "") ?: "" }

    val hasChanges = patientName != originalName || patientIC != originalIC ||
            doctorName != originalDrName || doctorEmail != originalEmail

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = { if (hasChanges) showDiscardDialog = true else onBack() }) {
                        Text("← Back", fontSize = 16.sp, color = Color(0xFF2196F3))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(paddingValues).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("👤 Patient Information", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFF2196F3), modifier = Modifier.padding(top = 8.dp))

            OutlinedTextField(value = patientName, onValueChange = { patientName = it; nameError = "" },
                label = { Text("Full Name", fontSize = 16.sp) },
                placeholder = { Text("e.g. Yugen Nair Prushothaman") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, isError = nameError.isNotEmpty(),
                supportingText = {
                    if (nameError.isNotEmpty()) Text(nameError, color = MaterialTheme.colorScheme.error)
                    else Text("As per IC / passport", color = Color.Gray, fontSize = 12.sp)
                }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))

            OutlinedTextField(
                value = patientIC,
                onValueChange = { if (it.length <= 12 && it.all { c -> c.isDigit() }) { patientIC = it; icError = "" } },
                label = { Text("IC Number", fontSize = 16.sp) },
                placeholder = { Text("e.g. 940720106883") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, isError = icError.isNotEmpty(),
                supportingText = {
                    if (icError.isNotEmpty()) Text(icError, color = MaterialTheme.colorScheme.error)
                    else Text("12-digit Malaysian IC • ${patientIC.length}/12", color = Color.Gray, fontSize = 12.sp)
                }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

            Spacer(modifier = Modifier.height(4.dp))

            Text("🩺 Doctor Information", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFF2196F3))

            OutlinedTextField(value = doctorName, onValueChange = { doctorName = it; drNameError = "" },
                label = { Text("Doctor's Name", fontSize = 16.sp) },
                placeholder = { Text("e.g. Dr. Ahmad Razali") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, isError = drNameError.isNotEmpty(),
                supportingText = {
                    if (drNameError.isNotEmpty()) Text(drNameError, color = MaterialTheme.colorScheme.error)
                    else Text("Include title e.g. Dr. / Prof.", color = Color.Gray, fontSize = 12.sp)
                }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))

            OutlinedTextField(value = doctorEmail, onValueChange = { doctorEmail = it; emailError = "" },
                label = { Text("Doctor's Email", fontSize = 16.sp) },
                placeholder = { Text("e.g. dr.ahmad@hospital.com") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, isError = emailError.isNotEmpty(),
                supportingText = {
                    if (emailError.isNotEmpty()) Text(emailError, color = MaterialTheme.colorScheme.error)
                    else Text("Report will be sent to this email", color = Color.Gray, fontSize = 12.sp)
                }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))

            Spacer(modifier = Modifier.height(8.dp))

            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Text("ℹ️", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Your information is stored safely on your phone only. " +
                            "It is used to generate your monthly report for your doctor.",
                        fontSize = 14.sp, color = Color(0xFF1565C0), lineHeight = 22.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    var valid = true
                    if (patientName.trim().isEmpty()) { nameError = "Please enter your full name"; valid = false }
                    if (patientIC.trim().length != 12) { icError = "IC number must be exactly 12 digits"; valid = false }
                    if (doctorName.trim().isEmpty()) { drNameError = "Please enter your doctor's name"; valid = false }
                    val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
                    if (!emailRegex.matches(doctorEmail.trim())) { emailError = "Please enter a valid email address"; valid = false }
                    if (valid) {
                        prefs.edit().apply {
                            putString("patient_name", patientName.trim())
                            putString("patient_ic",   patientIC.trim())
                            putString("doctor_name",  doctorName.trim())
                            putString("doctor_email", doctorEmail.trim())
                            apply()
                        }
                        showSavedDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) { Text("Save Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold) }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showSavedDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("✅ Settings Saved!", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Name: $patientName\nIC: $patientIC\nDoctor: $doctorName\nEmail: $doctorEmail",
                fontSize = 16.sp, lineHeight = 26.sp) },
            confirmButton = {
                Button(onClick = { showSavedDialog = false; onBack() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
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
                Button(onClick = { showDiscardDialog = false; onBack() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
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
    val today = remember { java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) }

    var selectedDateMillis by remember(monthCode) { mutableStateOf(System.currentTimeMillis()) }
    var displayDate by remember(monthCode) { mutableStateOf(today) }
    var sugarInput by remember(monthCode) { mutableStateOf("") }
    var stepsInput by remember(monthCode) { mutableStateOf("") }
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

    val dbHelper = remember { DatabaseHelper(context) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)

    val monthName = mapOf(
        "jan" to "January", "feb" to "February", "mar" to "March",
        "apr" to "April", "may" to "May", "jun" to "June",
        "jul" to "July", "aug" to "August", "sep" to "September",
        "oct" to "October", "nov" to "November", "dec" to "December"
    )[monthCode] ?: monthCode

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("Add Entry", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Month: $monthName", fontSize = 16.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))

        Card(modifier = Modifier.fillMaxWidth().height(80.dp), onClick = { showDatePicker = true }) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Date", fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(displayDate, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                }
                Text("📅", fontSize = 28.sp)
            }
        }

        OutlinedTextField(value = sugarInput, onValueChange = { sugarInput = it; sugarError = "" },
            label = { Text("Sugar Reading (mg/dL)") }, placeholder = { Text("e.g. 120") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, isError = sugarError.isNotEmpty(),
            supportingText = {
                if (sugarError.isNotEmpty()) Text(sugarError, color = MaterialTheme.colorScheme.error)
                else Text("Normal range: 70–140 mg/dL", color = Color.Gray, fontSize = 12.sp)
            }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

        OutlinedTextField(value = stepsInput, onValueChange = { stepsInput = it; stepsError = "" },
            label = { Text("Daily Steps") }, placeholder = { Text("e.g. 5000") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, isError = stepsError.isNotEmpty(),
            supportingText = {
                if (stepsError.isNotEmpty()) Text(stepsError, color = MaterialTheme.colorScheme.error)
                else Text("Goal: 7,000–10,000 steps/day", color = Color.Gray, fontSize = 12.sp)
            }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBack, modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                Text("Back", fontSize = 18.sp)
            }
            Button(
                onClick = {
                    var valid = true
                    val sugar = sugarInput.trim().toDoubleOrNull()
                    if (sugar == null) { sugarError = "Please enter a number"; valid = false }
                    else if (sugar < 1.0 || sugar > 600.0) { sugarError = "Must be between 1 and 600 mg/dL"; valid = false }
                    val steps = stepsInput.trim().toIntOrNull()
                    if (steps == null) { stepsError = "Please enter a whole number"; valid = false }
                    else if (steps < 0 || steps > 100000) { stepsError = "Must be between 0 and 100,000"; valid = false }
                    if (valid && sugar != null && steps != null) {
                        dbHelper.insertEntry(monthCode, displayDate, sugar, steps)
                        val allEntries = dbHelper.getEntries(monthCode)
                        averageSugar = if (allEntries.isNotEmpty()) allEntries.sumOf { it.sugar } / allEntries.size else 0.0
                        savedDate = displayDate; savedSugar = sugarInput; savedSteps = stepsInput
                        sugarInput = ""; stepsInput = ""
                        showSaveDialog = true
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) { Text("Save", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (showLast5) "Hide entries ▲" else "Last 5 Days — Tap to view ▼",
            color = Color(0xFF1565C0), fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable {
                last5Entries = dbHelper.getLast5Entries(monthCode)
                showLast5 = !showLast5
            }
        )

        if (showLast5) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Date",  modifier = Modifier.weight(1.4f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Sugar", modifier = Modifier.weight(1f),   fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Steps", modifier = Modifier.weight(1f),   fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("",      modifier = Modifier.width(40.dp))
                    }
                    Divider(modifier = Modifier.padding(vertical = 6.dp))
                    if (last5Entries.isEmpty()) {
                        Text("No entries yet.", color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        last5Entries.forEach { entry ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.date,             modifier = Modifier.weight(1.4f), fontSize = 13.sp)
                                Text("${entry.sugar} mg/dL", modifier = Modifier.weight(1f),   fontSize = 13.sp)
                                Text("${entry.steps}",       modifier = Modifier.weight(1f),   fontSize = 13.sp)
                                IconButton(onClick = { showDeleteConfirm = entry }, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
                                }
                            }
                            Divider(color = Color.LightGray, thickness = 0.5.dp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Close ▲", color = Color(0xFF1565C0), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showLast5 = false })
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
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
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel", fontSize = 18.sp) } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Saved!", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Date: $savedDate\nSugar: $savedSugar mg/dL\nSteps: $savedSteps\n\nMonthly average: ${String.format("%.1f", averageSugar)} mg/dL",
                fontSize = 18.sp, lineHeight = 28.sp) },
            confirmButton = {
                Button(onClick = { showSaveDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) { Text("OK", fontSize = 18.sp) }
            }
        )
    }

    showDeleteConfirm?.let { entryToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Entry?", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete:\n\nDate: ${entryToDelete.date}\nSugar: ${entryToDelete.sugar} mg/dL\nSteps: ${entryToDelete.steps}",
                fontSize = 16.sp, lineHeight = 26.sp) },
            confirmButton = {
                Button(onClick = {
                    dbHelper.deleteEntry(entryToDelete.id)
                    last5Entries = dbHelper.getLast5Entries(monthCode)
                    showDeleteConfirm = null
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) { Text("Delete", fontSize = 16.sp) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel", fontSize = 16.sp) } }
        )
    }
}

// ─────────────────────────────────────────────
// DATABASE HELPER
// ─────────────────────────────────────────────

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "health.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL("""CREATE TABLE entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            month TEXT NOT NULL, date TEXT NOT NULL,
            sugar REAL NOT NULL, steps INTEGER NOT NULL)""")
    }
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS entries"); onCreate(db)
    }
    fun insertEntry(month: String, date: String, sugar: Double, steps: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("month", month); put("date", date); put("sugar", sugar); put("steps", steps)
        }
        db.insert("entries", null, values); db.close()
    }
    fun deleteEntry(id: Int) {
        val db = writableDatabase
        db.delete("entries", "id = ?", arrayOf(id.toString())); db.close()
    }
    fun getEntries(month: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM entries WHERE month = ? ORDER BY date DESC", arrayOf(month))
        while (cursor.moveToNext()) {
            entries.add(Entry(cursor.getInt(cursor.getColumnIndexOrThrow("id")), month,
                cursor.getString(cursor.getColumnIndexOrThrow("date")),
                cursor.getDouble(cursor.getColumnIndexOrThrow("sugar")),
                cursor.getInt(cursor.getColumnIndexOrThrow("steps"))))
        }
        cursor.close(); db.close(); return entries
    }
    fun getLast5Entries(month: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM entries WHERE month = ? ORDER BY date DESC LIMIT 5", arrayOf(month))
        while (cursor.moveToNext()) {
            entries.add(Entry(cursor.getInt(cursor.getColumnIndexOrThrow("id")), month,
                cursor.getString(cursor.getColumnIndexOrThrow("date")),
                cursor.getDouble(cursor.getColumnIndexOrThrow("sugar")),
                cursor.getInt(cursor.getColumnIndexOrThrow("steps"))))
        }
        cursor.close(); db.close(); return entries
    }
    fun getAllEntriesForMonth(month: String): List<Entry> = getEntries(month)
}

// ─────────────────────────────────────────────
// DATA MODEL
// ─────────────────────────────────────────────

data class Entry(val id: Int, val month: String, val date: String, val sugar: Double, val steps: Int)
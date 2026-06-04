package com.example.a100life

import android.content.Context
import android.content.ContentValues
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
        WelcomeScreen(
            onContinue = {
                isFirstTime = false
                currentPage = "month"
            }
        )
    } else {
        when (currentPage) {
            "month" -> MonthSelectorScreen { monthCode ->
                selectedMonthCode = monthCode
                currentPage = "data"
            }
            "data" -> DataEntryScreen(selectedMonthCode, context) {
                currentPage = "month"
            }
            else -> MonthSelectorScreen { monthCode ->
                selectedMonthCode = monthCode
                currentPage = "data"
            }
        }
    }
}

// ─────────────────────────────────────────────
// WELCOME SCREEN
// ─────────────────────────────────────────────

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome to 100Life!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp),
            textAlign = TextAlign.Center
        )

        Text(
            text = "This app helps you track:\n\n• Sugar readings (morning)\n• Daily steps\n• Date\n\nYour data is saved safely on your phone.",
            fontSize = 22.sp,
            lineHeight = 32.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) {
            Text("Get Started", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────
// MONTH SELECTOR — FIX #2: All 12 months
//                 FIX #6: verticalScroll added
// ─────────────────────────────────────────────

@Composable
fun MonthSelectorScreen(onMonthSelected: (String) -> Unit) {

    // FIX #2: Full list of all 12 months
    val months = listOf(
        "January"   to "jan",
        "February"  to "feb",
        "March"     to "mar",
        "April"     to "apr",
        "May"       to "may",
        "June"      to "jun",
        "July"      to "jul",
        "August"    to "aug",
        "September" to "sep",
        "October"   to "oct",
        "November"  to "nov",
        "December"  to "dec"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) // FIX #6: scroll for small screens
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "100 Life Reading Tracker",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Select a month to record or view entries:",
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            months.forEach { (name, code) ->
                MonthButton(name, code) { onMonthSelected(it) }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun MonthButton(text: String, monthCode: String, onClick: (String) -> Unit) {
    Button(
        onClick = { onClick(monthCode) },
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
    ) {
        Text(text = text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────
// DATA ENTRY SCREEN
// FIX #3: Native DatePickerDialog (no more manual typing)
// FIX #4: Input validation with range check
// FIX #5: Delete button on each entry row
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataEntryScreen(monthCode: String, context: Context, onBack: () -> Unit) {

    val today = remember {
        java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }

    var selectedDateMillis by remember(monthCode) { mutableStateOf(System.currentTimeMillis()) }
    var displayDate by remember(monthCode) { mutableStateOf(today) }
    var sugarInput by remember(monthCode) { mutableStateOf("") }
    var stepsInput by remember(monthCode) { mutableStateOf("") }

    var showDatePicker by remember(monthCode) { mutableStateOf(false) }
    var showSaveDialog by remember(monthCode) { mutableStateOf(false) }
    var showDeleteConfirm by remember(monthCode) { mutableStateOf<Entry?>(null) }
    var showLast5 by remember(monthCode) { mutableStateOf(false) }

    // FIX #4: Validation error messages
    var sugarError by remember(monthCode) { mutableStateOf("") }
    var stepsError by remember(monthCode) { mutableStateOf("") }

    var savedDate by remember(monthCode) { mutableStateOf("") }
    var savedSugar by remember(monthCode) { mutableStateOf("") }
    var savedSteps by remember(monthCode) { mutableStateOf("") }
    var averageSugar by remember(monthCode) { mutableStateOf(0.0) }
    var last5Entries by remember(monthCode) { mutableStateOf(listOf<Entry>()) }

    val dbHelper = remember { DatabaseHelper(context) }

    // FIX #3: Native date picker state
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDateMillis
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Enter Readings",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Month: ${monthCode.replaceFirstChar { it.uppercase() }}",
            fontSize = 16.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(4.dp))

        // ── Date Field (tap to open calendar) ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            onClick = { showDatePicker = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Date", fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = displayDate, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                }
                Text(text = "📅", fontSize = 28.sp)
            }
        }

        // ── Sugar Reading Field ──
        OutlinedTextField(
            value = sugarInput,
            onValueChange = {
                sugarInput = it
                sugarError = "" // clear error on type
            },
            label = { Text("Sugar Reading (mg/dL)") },
            placeholder = { Text("e.g. 120") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = sugarError.isNotEmpty(),
            supportingText = {
                if (sugarError.isNotEmpty()) {
                    Text(sugarError, color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Normal range: 70–140 mg/dL", color = Color.Gray, fontSize = 12.sp)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        // ── Daily Steps Field ──
        OutlinedTextField(
            value = stepsInput,
            onValueChange = {
                stepsInput = it
                stepsError = "" // clear error on type
            },
            label = { Text("Daily Steps") },
            placeholder = { Text("e.g. 5000") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = stepsError.isNotEmpty(),
            supportingText = {
                if (stepsError.isNotEmpty()) {
                    Text(stepsError, color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Goal: 7,000–10,000 steps/day", color = Color.Gray, fontSize = 12.sp)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Action Buttons ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("Back", fontSize = 18.sp)
            }

            Button(
                onClick = {
                    // FIX #4: Full input validation
                    var valid = true

                    val sugar = sugarInput.trim().toDoubleOrNull()
                    if (sugar == null) {
                        sugarError = "Please enter a number"
                        valid = false
                    } else if (sugar < 1.0 || sugar > 600.0) {
                        sugarError = "Must be between 1 and 600 mg/dL"
                        valid = false
                    }

                    val steps = stepsInput.trim().toIntOrNull()
                    if (steps == null) {
                        stepsError = "Please enter a whole number"
                        valid = false
                    } else if (steps < 0 || steps > 100000) {
                        stepsError = "Must be between 0 and 100,000"
                        valid = false
                    }

                    if (valid && sugar != null && steps != null) {
                        dbHelper.insertEntry(monthCode, displayDate, sugar, steps)
                        val allEntries = dbHelper.getEntries(monthCode)
                        averageSugar = if (allEntries.isNotEmpty())
                            allEntries.sumOf { it.sugar } / allEntries.size
                        else 0.0
                        savedDate = displayDate
                        savedSugar = sugarInput
                        savedSteps = stepsInput
                        sugarInput = ""
                        stepsInput = ""
                        showSaveDialog = true
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("Save", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── Last 5 Entries Toggle ──
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (showLast5) "Hide entries ▲" else "Last 5 Days — Tap to view ▼",
            color = Color(0xFF1565C0),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable {
                last5Entries = dbHelper.getLast5Entries(monthCode)
                showLast5 = !showLast5
            }
        )

        // ── Last 5 Entries Table ──
        if (showLast5) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {

                    // Table header
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Date",  modifier = Modifier.weight(1.4f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Sugar", modifier = Modifier.weight(1f),   fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Steps", modifier = Modifier.weight(1f),   fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("",      modifier = Modifier.width(40.dp)) // delete col
                    }

                    Divider(modifier = Modifier.padding(vertical = 6.dp))

                    if (last5Entries.isEmpty()) {
                        Text(
                            "No entries yet for this month.",
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        last5Entries.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(entry.date,             modifier = Modifier.weight(1.4f), fontSize = 13.sp)
                                Text("${entry.sugar} mg/dL", modifier = Modifier.weight(1f),   fontSize = 13.sp)
                                Text("${entry.steps}",       modifier = Modifier.weight(1f),   fontSize = 13.sp)

                                // FIX #5: Delete button per row
                                IconButton(
                                    onClick = { showDeleteConfirm = entry },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete entry",
                                        tint = Color(0xFFE53935),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Divider(color = Color.LightGray, thickness = 0.5.dp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Close ▲",
                        color = Color(0xFF1565C0),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showLast5 = false }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // ── FIX #3: Native Android Date Picker Dialog ──
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val localDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                        displayDate = localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        selectedDateMillis = millis
                    }
                    showDatePicker = false
                }) {
                    Text("OK", fontSize = 18.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", fontSize = 18.sp)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── Save Success Dialog ──
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Saved!", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Date: $savedDate\n" +
                            "Sugar: $savedSugar mg/dL\n" +
                            "Steps: $savedSteps\n\n" +
                            "Monthly average: ${String.format("%.1f", averageSugar)} mg/dL",
                    fontSize = 18.sp,
                    lineHeight = 28.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSaveDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("OK", fontSize = 18.sp)
                }
            }
        )
    }

    // ── FIX #5: Delete Confirmation Dialog ──
    showDeleteConfirm?.let { entryToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Entry?", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete:\n\n" +
                            "Date: ${entryToDelete.date}\n" +
                            "Sugar: ${entryToDelete.sugar} mg/dL\n" +
                            "Steps: ${entryToDelete.steps}",
                    fontSize = 16.sp,
                    lineHeight = 26.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        dbHelper.deleteEntry(entryToDelete.id)
                        last5Entries = dbHelper.getLast5Entries(monthCode)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Delete", fontSize = 16.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel", fontSize = 16.sp)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────
// DATABASE HELPER
// FIX #1: SQL injection fixed — using ContentValues + parameterized queries
// FIX #5: deleteEntry() function added
// ─────────────────────────────────────────────

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "health.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(
            """CREATE TABLE entries (
                id      INTEGER PRIMARY KEY AUTOINCREMENT,
                month   TEXT    NOT NULL,
                date    TEXT    NOT NULL,
                sugar   REAL    NOT NULL,
                steps   INTEGER NOT NULL
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS entries")
        onCreate(db)
    }

    // FIX #1: Using ContentValues — completely safe, no SQL injection possible
    fun insertEntry(month: String, date: String, sugar: Double, steps: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("month", month)
            put("date",  date)
            put("sugar", sugar)
            put("steps", steps)
        }
        db.insert("entries", null, values)
        db.close()
    }

    // FIX #5: Delete a specific entry by its ID
    fun deleteEntry(id: Int) {
        val db = writableDatabase
        db.delete("entries", "id = ?", arrayOf(id.toString()))
        db.close()
    }

    fun getEntries(month: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM entries WHERE month = ? ORDER BY date DESC",
            arrayOf(month)
        )
        while (cursor.moveToNext()) {
            entries.add(
                Entry(
                    id    = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    month = month,
                    date  = cursor.getString(cursor.getColumnIndexOrThrow("date")),
                    sugar = cursor.getDouble(cursor.getColumnIndexOrThrow("sugar")),
                    steps = cursor.getInt(cursor.getColumnIndexOrThrow("steps"))
                )
            )
        }
        cursor.close()
        db.close()
        return entries
    }

    fun getLast5Entries(month: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM entries WHERE month = ? ORDER BY date DESC LIMIT 5",
            arrayOf(month)
        )
        while (cursor.moveToNext()) {
            entries.add(
                Entry(
                    id    = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    month = month,
                    date  = cursor.getString(cursor.getColumnIndexOrThrow("date")),
                    sugar = cursor.getDouble(cursor.getColumnIndexOrThrow("sugar")),
                    steps = cursor.getInt(cursor.getColumnIndexOrThrow("steps"))
                )
            )
        }
        cursor.close()
        db.close()
        return entries
    }
}

// ─────────────────────────────────────────────
// DATA MODEL
// ─────────────────────────────────────────────

data class Entry(
    val id:    Int,
    val month: String,
    val date:  String,
    val sugar: Double,
    val steps: Int
)
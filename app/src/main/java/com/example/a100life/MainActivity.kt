package com.example.a100life

import android.content.Context
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
    } else when (currentPage) {
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
            text = "👋 Welcome to 100Life!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
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

@Composable
fun MonthSelectorScreen(onMonthSelected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "100 Life Reading Tracker",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 40.dp)
        )

        Text(
            text = "Select a month:",
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            MonthButton("January", "jan") { onMonthSelected(it) }
            MonthButton("February", "feb") { onMonthSelected(it) }
            MonthButton("March", "mar") { onMonthSelected(it) }
            MonthButton("April", "apr") { onMonthSelected(it) }
        }
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

@Composable
fun DataEntryScreen(monthCode: String, context: Context, onBack: () -> Unit) {
    val today = remember { java.time.LocalDate.now().toString() }

    var reading1 by remember(monthCode) { mutableStateOf(today) }
    var reading2 by remember(monthCode) { mutableStateOf("") }
    var reading3 by remember(monthCode) { mutableStateOf("") }
    var showDialog by remember(monthCode) { mutableStateOf(false) }
    var showDatePicker by remember(monthCode) { mutableStateOf(false) }
    var savedDate by remember(monthCode) { mutableStateOf("") }
    var savedSugar by remember(monthCode) { mutableStateOf("") }
    var savedSteps by remember(monthCode) { mutableStateOf("") }
    var averageSugar by remember(monthCode) { mutableStateOf(0.0) }
    var showLast5 by remember(monthCode) { mutableStateOf(false) }
    var last5Entries by remember(monthCode) { mutableStateOf(listOf<Entry>()) }

    val dbHelper = remember { DatabaseHelper(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Enter Readings for $monthCode",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp),
            onClick = { showDatePicker = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Date",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = reading1,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }

                Text(
                    text = "📅",
                    fontSize = 28.sp
                )
            }
        }

        OutlinedTextField(
            value = reading2,
            onValueChange = { reading2 = it },
            label = { Text("Sugar Reading") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        OutlinedTextField(
            value = reading3,
            onValueChange = { reading3 = it },
            label = { Text("Daily Steps") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("Back")
            }

            Button(
                onClick = {
                    if (reading2.isNotEmpty() && reading3.isNotEmpty()) {
                        dbHelper.insertEntry(monthCode, reading1, reading2, reading3)
                        val allEntries = dbHelper.getEntries(monthCode)
                        var total = 0.0
                        for (entry in allEntries) {
                            total += entry.sugar
                        }
                        averageSugar = if (allEntries.isNotEmpty()) total / allEntries.size else 0.0
                        savedDate = reading1
                        savedSugar = reading2
                        savedSteps = reading3
                        showDialog = true
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("Save")
            }
        }

        Text(
            text = "Last 5 Days  - Tap here",
            color = Color(0xFF1565C0),
            fontSize  = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(top = 4.dp)
                .clickable {
                    last5Entries = dbHelper.getLast5Entries(monthCode)
                    showLast5 = !showLast5
                }
        )

        if (showLast5) {
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Date", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Sugar", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Steps", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (last5Entries.isEmpty()) {
                    Text("No entries found.", color = Color.Gray)
                } else {
                    last5Entries.forEach { entry ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(entry.date, modifier = Modifier.weight(1f))
                            Text(entry.sugar.toString(), modifier = Modifier.weight(1f))
                            Text(entry.steps.toString(), modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Done",
                    color = Color(0xFF1565C0),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        showLast5 = false
                    }
                )
            }
        }
    }

    if (showDatePicker) {
        var selectedDate by remember { mutableStateOf(reading1) }

        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text("Select Date", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Choose any date to enter data:", fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = selectedDate,
                        onValueChange = { selectedDate = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "💡 Tip: Format is YYYY-MM-DD (e.g., 2026-06-02)",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        reading1 = selectedDate
                        showDatePicker = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Text("OK", fontSize = 16.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("✓ Saved!", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Date: " + savedDate +
                            "\nSugar: " + savedSugar + " mg/dL" +
                            "\nSteps: " + savedSteps +
                            "\n\nAverage for " + monthCode + ": " + String.format("%.1f", averageSugar) + " mg/dL"
                )
            },
            confirmButton = {
                Button(onClick = { showDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "health.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, month TEXT NOT NULL, date TEXT NOT NULL, sugar REAL NOT NULL, steps INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS entries")
        onCreate(db)
    }

    fun insertEntry(month: String, date: String, sugar: String, steps: String) {
        val db = writableDatabase
        db.execSQL("INSERT INTO entries (month, date, sugar, steps) VALUES ('" + month + "', '" + date + "', " + sugar + ", " + steps + ")")
        db.close()
    }

    fun getEntries(month: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM entries WHERE month = ?", arrayOf(month))

        while (cursor.moveToNext()) {
            val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
            val date = cursor.getString(cursor.getColumnIndexOrThrow("date"))
            val sugar = cursor.getDouble(cursor.getColumnIndexOrThrow("sugar"))
            val steps = cursor.getInt(cursor.getColumnIndexOrThrow("steps"))
            entries.add(Entry(id, month, date, sugar, steps))
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
            val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
            val date = cursor.getString(cursor.getColumnIndexOrThrow("date"))
            val sugar = cursor.getDouble(cursor.getColumnIndexOrThrow("sugar"))
            val steps = cursor.getInt(cursor.getColumnIndexOrThrow("steps"))
            entries.add(Entry(id, month, date, sugar, steps))
        }

        cursor.close()
        db.close()
        return entries
    }
}

data class Entry(val id: Int, val month: String, val date: String, val sugar: Double, val steps: Int)

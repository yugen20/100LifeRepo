package com.example.a100life

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Back", fontSize = 16.sp, color = Color(0xFF2196F3))
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                "Last updated: June 2026",
                fontSize = 13.sp,
                color = Color.Gray
            )

            PolicySection(
                emoji = "📱",
                title = "Data Storage",
                body = "All your health data — including sugar readings, steps, dates and notes — is stored locally on your phone only. We do not upload your data to any server or cloud service."
            )

            PolicySection(
                emoji = "🌐",
                title = "No Internet Required",
                body = "100Life works fully offline. The app does not require an internet connection to function. No data is ever sent to our servers because we have no servers."
            )

            PolicySection(
                emoji = "📊",
                title = "What We Store on Your Phone",
                body = "• Your name and IC number (for report generation)\n• Your doctor's name and email address\n• Your daily sugar readings and step counts\n• Optional meal notes\n• Your preferred glucose unit and reminder settings"
            )

            PolicySection(
                emoji = "📧",
                title = "Data Sharing",
                body = "Your health data is only shared when YOU explicitly tap 'Send Report to Doctor'. The report is sent directly from your phone's email app to your doctor's email address. We never see, store or process this email."
            )

            PolicySection(
                emoji = "🔒",
                title = "Your Data, Your Control",
                body = "You can delete any entry at any time from within the app. Uninstalling the app permanently removes all stored data from your device. We have no copy of your data."
            )

            PolicySection(
                emoji = "⚕️",
                title = "Medical Disclaimer",
                body = "100Life is a personal health tracking tool only. It is NOT a medical device. The HbA1c prediction feature is a mathematical estimate and NOT a substitute for your doctor's blood test. Always consult your doctor for medical advice and treatment decisions."
            )

            PolicySection(
                emoji = "👶",
                title = "Children's Privacy",
                body = "This app is intended for adults managing their health conditions. We do not knowingly collect data from children under 13."
            )

            PolicySection(
                emoji = "📬",
                title = "Contact",
                body = "If you have any questions about this privacy policy, please contact us through the Google Play Store listing for 100Life."
            )

            Divider()

            Text(
                "100Life is a free app built to help people track their health and communicate better with their doctors. Your privacy is our priority.",
                fontSize = 13.sp,
                color = Color.Gray,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun PolicySection(emoji: String, title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(emoji, fontSize = 18.sp)
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1565C0)
            )
        }
        Text(
            body,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = Color(0xFF444444)
        )
    }
}
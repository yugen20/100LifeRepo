package com.example.a100life

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────
// ABOUT DEVELOPER SCREEN
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDeveloperScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = AppColors.PageBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "About the Developer",
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
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon badge
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(AppColors.NavyMid, shape = RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("100", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.TextOnDark)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Why I Built 100Life",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextDark,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.CardWhite),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "In 2025, I went through a 9-hour brain tumor surgery. Before that, " +
                                "undiagnosed diabetes and a blood sugar episode had already taken a toll on my body.",
                        fontSize = 16.sp, lineHeight = 26.sp, color = AppColors.TextDark
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "After surgery, my rheumatologist advised me to take my blood sugar seriously — " +
                                "to track it, manage it, and stay active. So I started walking daily and recording " +
                                "every reading without fail.",
                        fontSize = 16.sp, lineHeight = 26.sp, color = AppColors.TextDark
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Six months later, my HbA1c reached 5.0 — a number I'm genuinely proud of. " +
                                "But there was one problem: I was tracking everything through WhatsApp messages " +
                                "and scraps of paper, sending blurry photos to my doctor every week. It worked, but barely.",
                        fontSize = 16.sp, lineHeight = 26.sp, color = AppColors.TextDark
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "So I built 100Life — first for myself, then for anyone else going through the same thing.",
                        fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = AppColors.NavyMid
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.CardBlue),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "💙 Built to Be Free",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.NavyMid
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "100Life is completely free — especially for elderly users who shouldn't have to pay " +
                                "a subscription just to take care of their health. There's no cloud storage, NO AI HYPE!, no monthly " +
                                "fee, no ads. Your data stays on your phone, where it belongs. keep track and stay healthy. " +
                                "I will keep improving the feature as much as i can",
                        fontSize = 15.sp, lineHeight = 24.sp, color = AppColors.TextDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "This app is part of my own recovery story.\nI hope it helps you — or someone you love —\ntake care of theirs too.",
                fontSize = 16.sp,
                lineHeight = 26.sp,
                fontStyle = FontStyle.Italic,
                color = AppColors.TextGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "— Call me Y.P",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.NavyMid
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
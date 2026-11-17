package com.example.ui.screens.auth

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.VaultAtmosphereBackground
import com.example.ui.components.VaultGradientButton
import com.example.ui.components.VaultoraLogoBadge
import com.example.ui.theme.LocalVaultColors
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val description: String,
    val badge: String,
    val imageRes: Int,
    val isMedicine: Boolean = false
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val vaultColors = LocalVaultColors.current
    val pages = listOf(
        OnboardingPage(
            title = "Never lose a purchase receipt.",
            description = "Scan physical bills with AI or upload digital PDFs and images. Every invoice stays indexed and instantly searchable.",
            badge = "PURCHASE VAULT",
            imageRes = R.drawable.img_onboarding_receipts
        ),
        OnboardingPage(
            title = "Never miss a return or warranty deadline.",
            description = "Track return windows and manufacturer warranty countdowns in real time, and draft claim emails in seconds.",
            badge = "WARRANTY SHIELD",
            imageRes = R.drawable.img_onboarding_warranty
        ),
        OnboardingPage(
            title = "Keep medicine expiry information organized.",
            description = "Store medicine photos, batch numbers, and expiry dates with timely alerts in a dedicated green vault.",
            badge = "MEDICINE VAULT",
            imageRes = R.drawable.img_onboarding_medicine,
            isMedicine = true
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val currentPage = pages[pagerState.currentPage]
    val activeAccent = if (currentPage.isMedicine) vaultColors.medicineAccent else vaultColors.purchaseAccent

    VaultAtmosphereBackground(
        accentColor = activeAccent,
        secondaryAccent = vaultColors.goldAccent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.Center)
                .testTag("onboarding_screen"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VaultoraLogoBadge(size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "VAULTORA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(
                    onClick = onComplete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("onboarding_skip_button")
                ) {
                    Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { index ->
                val page = pages[index]
                val pageAccent = if (page.isMedicine) vaultColors.medicineAccent else vaultColors.purchaseAccent
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(1.dp, pageAccent.copy(alpha = 0.45f)),
                        color = Color(0xFF0C0F16),
                        shadowElevation = 12.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    ) {
                        Image(
                            painter = painterResource(id = page.imageRes),
                            contentDescription = page.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = pageAccent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, pageAccent.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = page.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = pageAccent,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = page.description,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val pageGradient = when (pagerState.currentPage) {
                0 -> vaultColors.purchaseButtonGradient
                1 -> vaultColors.brandButtonGradient
                else -> vaultColors.medicineButtonGradient
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                repeat(pages.size) { idx ->
                    val selected = pagerState.currentPage == idx
                    val dotWidth by animateDpAsState(
                        targetValue = if (selected) 28.dp else 8.dp,
                        animationSpec = tween(durationMillis = 260),
                        label = "onboarding_dot_width"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    Brush.horizontalGradient(colors = pageGradient)
                                } else {
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.outline,
                                            MaterialTheme.colorScheme.outline
                                        )
                                    )
                                }
                            )
                    )
                }
            }

            val isLast = pagerState.currentPage == pages.lastIndex
            VaultGradientButton(
                onClick = {
                    if (isLast) {
                        onComplete()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                colors = pageGradient,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("onboarding_next_button")
            ) {
                Text(
                    text = if (isLast) "Enter Your Vault" else "Continue",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

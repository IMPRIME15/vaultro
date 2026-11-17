package com.example.ui.screens.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.ui.theme.LocalVaultColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class CosmicStar(
    val xFraction: Float,
    val yFraction: Float,
    val radiusPx: Float,
    val phaseOffset: Float,
    val colorIndex: Int
)

@Composable
fun SplashScreen(
    hasSeenOnboarding: Boolean,
    onAuthenticated: () -> Unit,
    onGoOnboarding: () -> Unit,
    onGoLogin: () -> Unit
) {
    val context = LocalContext.current
    val vaultColors = LocalVaultColors.current

    // Ambient continuous cosmic motion so peak reveal stays alive
    val infiniteTransition = rememberInfiniteTransition(label = "splash_ambient")
    val ambientPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambient_pulse"
    )
    val wordmarkShimmer by infiniteTransition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wordmark_shimmer"
    )

    val cosmicStars = remember {
        List(28) { idx ->
            val goldenAngle = idx * 2.399963f
            val dist = 0.14f + (idx / 28f) * 0.42f
            CosmicStar(
                xFraction = (0.5f + dist * cos(goldenAngle)).coerceIn(0.05f, 0.95f),
                yFraction = (0.46f + dist * sin(goldenAngle) * 0.9f).coerceIn(0.06f, 0.92f),
                radiusPx = if (idx % 5 == 0) 4.2f else if (idx % 2 == 0) 2.8f else 1.9f,
                phaseOffset = (idx * 0.37f) % 1f,
                colorIndex = idx % 4
            )
        }
    }

    // JioHotstar-inspired multi-stage choreographed intro
    val auroraScale = remember { Animatable(0.52f) }
    val convergenceProgress = remember { Animatable(0f) }
    val starburstScale = remember { Animatable(0.05f) }
    val starburstAlpha = remember { Animatable(0f) }
    val starburstRotation = remember { Animatable(-48f) }
    val anamorphicFlare = remember { Animatable(0f) }
    val shockwaveProgress = remember { Animatable(0f) }

    val logoScale = remember { Animatable(0.24f) }
    val logoAlpha = remember { Animatable(0f) }
    val outerRingRotation = remember { Animatable(0f) }
    val innerRingRotation = remember { Animatable(180f) }
    val sheenOffset = remember { Animatable(-1.3f) }

    val wordmarkAlpha = remember { Animatable(0f) }
    val wordmarkSlideY = remember { Animatable(32f) }
    val letterSpacingAnim = remember { Animatable(13.5f) }
    val pillAlpha = remember { Animatable(0f) }
    val pillScale = remember { Animatable(0.9f) }
    val beamProgress = remember { Animatable(0f) }

    val exitScale = remember { Animatable(1f) }
    val exitAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // Stage 1: Cosmic aurora bloom + inward convergence rays + starburst ignition
        launch {
            auroraScale.animateTo(
                targetValue = 1.22f,
                animationSpec = tween(durationMillis = 1900, easing = FastOutSlowInEasing)
            )
        }
        launch {
            convergenceProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
            )
        }
        delay(120L)
        launch {
            starburstAlpha.animateTo(1f, animationSpec = tween(170))
            starburstAlpha.animateTo(0f, animationSpec = tween(460, delayMillis = 240))
        }
        launch {
            starburstScale.animateTo(
                targetValue = 1.72f,
                animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing)
            )
        }
        launch {
            starburstRotation.animateTo(
                targetValue = 45f,
                animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing)
            )
        }
        launch {
            anamorphicFlare.animateTo(1f, animationSpec = tween(210, easing = FastOutSlowInEasing))
            anamorphicFlare.animateTo(0f, animationSpec = tween(420, delayMillis = 120))
        }
        launch {
            shockwaveProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
            )
        }

        // Stage 2: Dual orbital prism halos + spring emblem pop + diagonal specular sweep
        delay(160L)
        launch {
            logoAlpha.animateTo(1f, animationSpec = tween(320, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            outerRingRotation.animateTo(
                targetValue = 420f,
                animationSpec = tween(durationMillis = 1950, easing = LinearEasing)
            )
        }
        launch {
            innerRingRotation.animateTo(
                targetValue = -160f,
                animationSpec = tween(durationMillis = 1950, easing = LinearEasing)
            )
        }
        launch {
            delay(200L)
            sheenOffset.animateTo(
                targetValue = 1.7f,
                animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing)
            )
        }

        // Stage 3: Kinetic VAULTORA wordmark + frosted capability pill + aurora beam
        delay(300L)
        launch {
            wordmarkAlpha.animateTo(1f, animationSpec = tween(420, easing = FastOutSlowInEasing))
        }
        launch {
            wordmarkSlideY.animateTo(0f, animationSpec = tween(460, easing = FastOutSlowInEasing))
        }
        launch {
            letterSpacingAnim.animateTo(5.8f, animationSpec = tween(560, easing = FastOutSlowInEasing))
        }
        launch {
            delay(170L)
            pillAlpha.animateTo(1f, animationSpec = tween(360, easing = FastOutSlowInEasing))
        }
        launch {
            delay(170L)
            pillScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
            )
        }
        launch {
            delay(150L)
            beamProgress.animateTo(1f, animationSpec = tween(820, easing = FastOutSlowInEasing))
        }

        // Peak cosmic hold, then smooth zoom-dissolve into the vault
        delay(1180L)
        launch {
            exitScale.animateTo(1.11f, animationSpec = tween(280, easing = FastOutSlowInEasing))
        }
        exitAlpha.animateTo(0f, animationSpec = tween(280, easing = FastOutSlowInEasing))

        val credentialManager = CredentialManager.create(context)
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthenticated,
            onUnauthenticated = {
                if (hasSeenOnboarding) onGoLogin() else onGoOnboarding()
            },
            scope = this
        )
    }

    val cosmicBg = Color(0xFF040612)
    val brandPalette = vaultColors.brandButtonGradient
    val purchasePalette = vaultColors.purchaseButtonGradient
    val medicinePalette = vaultColors.medicineButtonGradient
    val starPalette = listOf(Color.White, brandPalette[2], purchasePalette[2], vaultColors.goldAccent)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cosmicBg)
            .graphicsLayer {
                alpha = exitAlpha.value
                scaleX = exitScale.value
                scaleY = exitScale.value
            }
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Deep multi-color cosmic nebula backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = 68.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val breathe = 1f + 0.05f * sin(ambientPulse * 2f * PI.toFloat())
                val s = auroraScale.value * breathe
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            purchasePalette[1].copy(alpha = 0.56f),
                            purchasePalette[2].copy(alpha = 0.26f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.34f),
                        radius = size.minDimension * 0.68f * s
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            brandPalette[0].copy(alpha = 0.50f),
                            brandPalette[2].copy(alpha = 0.26f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.14f, size.height * 0.64f),
                        radius = size.minDimension * 0.60f * s
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            medicinePalette[1].copy(alpha = 0.36f),
                            brandPalette[2].copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.86f, size.height * 0.70f),
                        radius = size.minDimension * 0.56f * s
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            vaultColors.goldAccent.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.52f, size.height * 0.52f),
                        radius = size.minDimension * 0.42f * s
                    )
                )
            }
        }

        // Twinkling cosmic starfield & converging ignition streaks
        Canvas(modifier = Modifier.fillMaxSize()) {
            cosmicStars.forEach { star ->
                val twinkle = 0.30f + 0.70f * ((sin((ambientPulse + star.phaseOffset) * 2f * PI.toFloat()) + 1f) * 0.5f)
                drawCircle(
                    color = starPalette[star.colorIndex].copy(alpha = twinkle * 0.85f),
                    radius = star.radiusPx,
                    center = Offset(size.width * star.xFraction, size.height * star.yFraction)
                )
            }

            // Converging light streaks during initial ignition (Stage 1)
            val conv = convergenceProgress.value
            if (conv in 0.01f..0.98f) {
                val center = Offset(size.width * 0.5f, size.height * 0.43f)
                val rayCount = 8
                val rayAlpha = (1f - conv) * 0.55f
                for (i in 0 until rayCount) {
                    val angle = (i * (360f / rayCount) + 22.5f) * (PI / 180.0)
                    val outerDist = size.minDimension * (0.48f * (1f - conv) + 0.06f)
                    val innerDist = size.minDimension * (0.16f * (1f - conv))
                    val start = Offset(
                        center.x + (outerDist * cos(angle)).toFloat(),
                        center.y + (outerDist * sin(angle)).toFloat()
                    )
                    val end = Offset(
                        center.x + (innerDist * cos(angle)).toFloat(),
                        center.y + (innerDist * sin(angle)).toFloat()
                    )
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                brandPalette[2].copy(alpha = rayAlpha),
                                Color.White.copy(alpha = rayAlpha)
                            ),
                            start = start,
                            end = end
                        ),
                        start = start,
                        end = end,
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(216.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.minDimension / 2f

                    // Expanding prismatic shockwave pulse
                    val sw = shockwaveProgress.value
                    if (sw in 0.01f..0.99f) {
                        val swAlpha = (1f - sw) * 0.90f
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    brandPalette[2].copy(alpha = swAlpha),
                                    purchasePalette[2].copy(alpha = swAlpha),
                                    vaultColors.goldAccent.copy(alpha = swAlpha),
                                    brandPalette[0].copy(alpha = swAlpha),
                                    brandPalette[2].copy(alpha = swAlpha)
                                ),
                                center = center
                            ),
                            radius = maxRadius * (0.24f + 0.76f * sw),
                            center = center,
                            style = Stroke(width = (6f * (1f - sw)).coerceAtLeast(1.5f))
                        )
                    }

                    // Dual counter-rotating orbital prism rings + comet tips
                    if (logoAlpha.value > 0.05f) {
                        val outerRadius = maxRadius * 0.78f
                        rotate(degrees = outerRingRotation.value, pivot = center) {
                            drawCircle(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        brandPalette[2].copy(alpha = 0.85f * logoAlpha.value),
                                        purchasePalette[2].copy(alpha = 0.90f * logoAlpha.value),
                                        vaultColors.goldAccent.copy(alpha = 0.78f * logoAlpha.value),
                                        Color.Transparent
                                    ),
                                    center = center
                                ),
                                radius = outerRadius,
                                center = center,
                                style = Stroke(width = 4f, cap = StrokeCap.Round)
                            )
                            // Luminous comet orb on the outer ring
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.95f * logoAlpha.value),
                                        brandPalette[2].copy(alpha = 0.7f * logoAlpha.value),
                                        Color.Transparent
                                    ),
                                    center = Offset(center.x + outerRadius, center.y),
                                    radius = 12f
                                ),
                                radius = 12f,
                                center = Offset(center.x + outerRadius, center.y)
                            )
                        }

                        val innerRadius = maxRadius * 0.67f
                        rotate(degrees = innerRingRotation.value, pivot = center) {
                            drawCircle(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        medicinePalette[1].copy(alpha = 0.65f * logoAlpha.value),
                                        brandPalette[1].copy(alpha = 0.70f * logoAlpha.value),
                                        Color.Transparent
                                    ),
                                    center = center
                                ),
                                radius = innerRadius,
                                center = center,
                                style = Stroke(width = 2.4f, cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Anamorphic cinema horizontal lens-flare streak
                    val flare = anamorphicFlare.value
                    if (flare > 0.02f) {
                        val halfWidth = maxRadius * (0.4f + 0.6f * flare)
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    brandPalette[2].copy(alpha = 0.85f * flare),
                                    Color.White.copy(alpha = flare),
                                    purchasePalette[2].copy(alpha = 0.85f * flare),
                                    Color.Transparent
                                ),
                                startX = center.x - halfWidth,
                                endX = center.x + halfWidth
                            ),
                            start = Offset(center.x - halfWidth, center.y),
                            end = Offset(center.x + halfWidth, center.y),
                            strokeWidth = 4.5f * flare + 1.5f,
                            cap = StrokeCap.Round
                        )
                    }

                    // Layered 4-point JioHotstar diamond starburst spark + 45 deg cross sparkle
                    if (starburstAlpha.value > 0.01f) {
                        val rOuter = maxRadius * 0.78f * starburstScale.value
                        val rInner = rOuter * 0.13f
                        rotate(degrees = starburstRotation.value, pivot = center) {
                            val starPath = Path().apply {
                                moveTo(center.x, center.y - rOuter)
                                lineTo(center.x + rInner, center.y - rInner)
                                lineTo(center.x + rOuter, center.y)
                                lineTo(center.x + rInner, center.y + rInner)
                                lineTo(center.x, center.y + rOuter)
                                lineTo(center.x - rInner, center.y + rInner)
                                lineTo(center.x - rOuter, center.y)
                                lineTo(center.x - rInner, center.y - rInner)
                                close()
                            }
                            drawPath(
                                path = starPath,
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = starburstAlpha.value),
                                        brandPalette[2].copy(alpha = 0.85f * starburstAlpha.value),
                                        purchasePalette[2].copy(alpha = 0.50f * starburstAlpha.value),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = rOuter
                                )
                            )
                        }
                        val rCross = rOuter * 0.56f
                        val rCrossInner = rCross * 0.14f
                        rotate(degrees = starburstRotation.value + 45f, pivot = center) {
                            val crossPath = Path().apply {
                                moveTo(center.x, center.y - rCross)
                                lineTo(center.x + rCrossInner, center.y - rCrossInner)
                                lineTo(center.x + rCross, center.y)
                                lineTo(center.x + rCrossInner, center.y + rCrossInner)
                                lineTo(center.x, center.y + rCross)
                                lineTo(center.x - rCrossInner, center.y + rCrossInner)
                                lineTo(center.x - rCross, center.y)
                                lineTo(center.x - rCrossInner, center.y - rCrossInner)
                                close()
                            }
                            drawPath(
                                path = crossPath,
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.9f * starburstAlpha.value),
                                        vaultColors.goldAccent.copy(alpha = 0.65f * starburstAlpha.value),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = rCross
                                )
                            )
                        }
                    }
                }

                // Emblem Card with prismatic border + diagonal specular light sweep
                Surface(
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer {
                            alpha = logoAlpha.value
                            scaleX = logoScale.value
                            scaleY = logoScale.value
                        },
                    shape = RoundedCornerShape(32.dp),
                    color = Color(0xFFFBF8FD),
                    border = BorderStroke(
                        width = 1.8.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.95f),
                                purchasePalette[1],
                                brandPalette[2],
                                medicinePalette[1]
                            )
                        )
                    ),
                    shadowElevation = 24.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_vaultora_logo),
                            contentDescription = "VAULTORA Emblem",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val s = sheenOffset.value
                            if (s in -1.2f..1.6f) {
                                val startX = size.width * s
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.White.copy(alpha = 0.56f),
                                            brandPalette[2].copy(alpha = 0.25f),
                                            Color.Transparent
                                        ),
                                        start = Offset(startX - size.width * 0.45f, 0f),
                                        end = Offset(startX + size.width * 0.45f, size.height)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Shimmering Gradient VAULTORA Wordmark
            val shimmerBrush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.White,
                    (wordmarkShimmer - 0.18f).coerceIn(0f, 1f) to Color(0xFFD8C8FF),
                    wordmarkShimmer.coerceIn(0f, 1f) to Color.White,
                    (wordmarkShimmer + 0.18f).coerceIn(0f, 1f) to brandPalette[2],
                    1f to purchasePalette[2]
                )
            )
            Text(
                text = "VAULTORA",
                style = MaterialTheme.typography.displayLarge.merge(
                    TextStyle(
                        letterSpacing = letterSpacingAnim.value.sp,
                        fontWeight = FontWeight.ExtraBold,
                        brush = shimmerBrush
                    )
                ),
                modifier = Modifier.graphicsLayer {
                    alpha = wordmarkAlpha.value
                    translationY = wordmarkSlideY.value
                }
            )

            Spacer(Modifier.height(10.dp))

            // Frosted cosmic capability glass pill
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        colors = listOf(
                            purchasePalette[1].copy(alpha = 0.65f),
                            brandPalette[2].copy(alpha = 0.65f),
                            medicinePalette[1].copy(alpha = 0.65f)
                        )
                    )
                ),
                modifier = Modifier.graphicsLayer {
                    alpha = pillAlpha.value
                    scaleX = pillScale.value
                    scaleY = pillScale.value
                }
            ) {
                Text(
                    text = "PURCHASES • WARRANTIES • MEDICINES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.8.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFFEBE4FF),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(34.dp))

            // Multi-color aurora progress ribbon with luminous leading star orb
            Box(
                modifier = Modifier
                    .width(184.dp)
                    .height(6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.14f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(beamProgress.value.coerceIn(0.04f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    purchasePalette[0],
                                    purchasePalette[2],
                                    brandPalette[2],
                                    medicinePalette[1]
                                )
                            )
                        )
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth(beamProgress.value.coerceIn(0.04f, 1f))
                        .height(6.dp)
                ) {
                    val tip = Offset(size.width, size.height / 2f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White,
                                brandPalette[2].copy(alpha = 0.85f),
                                Color.Transparent
                            ),
                            center = tip,
                            radius = 12f
                        ),
                        radius = 12f,
                        center = tip
                    )
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalVaultColors
import com.example.util.DateAndCurrencyUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.vaultSharedBounds(key: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current
    val animScope = LocalAnimatedVisibilityScope.current
    if (sharedScope == null || animScope == null) return this
    return with(sharedScope) {
        this@vaultSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animScope,
            boundsTransform = BoundsTransform { _, _ ->
                spring(
                    dampingRatio = 0.88f,
                    stiffness = Spring.StiffnessMediumLow
                )
            }
        )
    }
}

@Composable
fun VaultAnimatedEntrance(
    delayMillis: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var hasAnimated by rememberSaveable { mutableStateOf(false) }
    val alpha = remember { Animatable(if (hasAnimated) 1f else 0f) }
    val offsetY = remember { Animatable(if (hasAnimated) 0f else 24f) }
    val scale = remember { Animatable(if (hasAnimated) 1f else 0.975f) }

    LaunchedEffect(Unit) {
        if (!hasAnimated) {
            launch {
                alpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 360, delayMillis = delayMillis, easing = FastOutSlowInEasing)
                )
            }
            launch {
                offsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 380, delayMillis = delayMillis, easing = FastOutSlowInEasing)
                )
            }
            launch {
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
                )
            }
            hasAnimated = true
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha.value
            this.translationY = offsetY.value
            this.scaleX = scale.value
            this.scaleY = scale.value
        }
    ) {
        content()
    }
}

@Composable
fun VaultAtmosphereBackground(
    modifier: Modifier = Modifier,
    accentColor: Color = LocalVaultColors.current.goldAccent,
    secondaryAccent: Color = LocalVaultColors.current.purchaseAccent,
    content: @Composable BoxScope.() -> Unit
) {
    val bgColor = MaterialTheme.colorScheme.background
    val isDark = LocalVaultColors.current.isDark
    val midPrism = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Diffused backdrop prism layer with real RenderEffect blur (API 31+) & smooth radial falloff
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = 52.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .drawBehind {
                    val topAlpha = if (isDark) 0.44f else 0.30f
                    val bottomAlpha = if (isDark) 0.34f else 0.24f
                    val centerAlpha = if (isDark) 0.24f else 0.16f
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                accentColor.copy(alpha = if (isDark) 0.12f else 0.08f),
                                Color.Transparent,
                                secondaryAccent.copy(alpha = if (isDark) 0.12f else 0.08f)
                            )
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = topAlpha), Color.Transparent),
                            center = Offset(size.width * 0.86f, size.height * 0.10f),
                            radius = size.width * 0.78f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(midPrism.copy(alpha = centerAlpha), Color.Transparent),
                            center = Offset(size.width * 0.50f, size.height * 0.46f),
                            radius = size.width * 0.68f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(secondaryAccent.copy(alpha = bottomAlpha), Color.Transparent),
                            center = Offset(size.width * 0.12f, size.height * 0.82f),
                            radius = size.width * 0.78f
                        )
                    )
                }
        )
        content()
    }
}

@Composable
fun VaultoraLogoBadge(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val vaultColors = LocalVaultColors.current
    val cornerRadius = size * 0.26f
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(cornerRadius),
        color = Color(0xFFFBF8FD),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    vaultColors.glassHighlightBorder,
                    vaultColors.purchaseAccent.copy(alpha = 0.6f)
                )
            )
        ),
        shadowElevation = 8.dp
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_vaultora_logo),
            contentDescription = "VAULTORA logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius))
        )
    }
}

@Composable
fun GlassVaultCard(
    modifier: Modifier = Modifier,
    accentBorder: Color? = null,
    containerBrush: Brush? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val vaultColors = LocalVaultColors.current
    val shape = RoundedCornerShape(22.dp)
    val tintAccent = accentBorder ?: vaultColors.glassBorder
    val rimBrush = Brush.linearGradient(
        colors = listOf(
            vaultColors.glassHighlightBorder,
            tintAccent,
            vaultColors.glassShadowBorder
        )
    )
    val fillBrush = containerBrush ?: Brush.linearGradient(
        colors = listOf(
            vaultColors.glassSheenStart,
            vaultColors.glassSurface,
            vaultColors.glassSheenEnd
        )
    )

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
    ) {
        // Blurred internal prism backdrop refraction clipped to card bounds
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(radius = 24.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                tintAccent.copy(alpha = if (vaultColors.isDark) 0.20f else 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.15f, size.height * 0.12f),
                            radius = size.maxDimension * 0.75f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (vaultColors.isDark) 0.08f else 0.28f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.85f, size.height * 0.88f),
                            radius = size.maxDimension * 0.6f
                        )
                    )
                }
        )

        // Semi-transparent frosted glass surface + specular rim border
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(fillBrush, shape)
                .border(width = 1.dp, brush = rimBrush, shape = shape)
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun StatusPill(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.56f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun MetricStatTile(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    GlassVaultCard(
        modifier = modifier,
        accentBorder = accent.copy(alpha = 0.42f),
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.20f))
                    .border(1.dp, accent.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    val vaultColors = LocalVaultColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(vaultColors.glassBarSurface.copy(alpha = if (vaultColors.isDark) 0.72f else 0.80f))
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onBackground
            ),
            navigationIcon = {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("top_bar_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                }
            },
            title = {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            actions = { actions() }
        )
        HorizontalDivider(
            thickness = 1.dp,
            color = vaultColors.glassHighlightBorder.copy(alpha = if (vaultColors.isDark) 0.18f else 0.55f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDatePickerField(
    label: String,
    selectedMillis: Long?,
    onDateSelected: (Long?) -> Unit,
    optional: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    testTag: String = "date_picker_field"
) {
    val vaultColors = LocalVaultColors.current
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable { showDialog = true }
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = vaultColors.glassSurface.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, vaultColors.glassBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (selectedMillis != null && selectedMillis > 0L) {
                            DateAndCurrencyUtils.formatDate(selectedMillis)
                        } else {
                            if (optional) "Optional — tap to set" else "Tap to select date"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selectedMillis != null && selectedMillis > 0L) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            if (optional && selectedMillis != null && selectedMillis > 0L) {
                IconButton(
                    onClick = { onDateSelected(null) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Clear $label",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showDialog) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = selectedMillis ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDateSelected(state.selectedDateMillis)
                        showDialog = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            ProvideSafeTextToolbar {
                DatePicker(state = state)
            }
        }
    }
}

@Composable
fun VaultEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    GlassVaultCard(
        modifier = modifier.fillMaxWidth(),
        accentBorder = accent.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .border(1.dp, accent.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun VaultGradientButton(
    onClick: () -> Unit,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = Color.White,
    shape: Shape = RoundedCornerShape(16.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    val activeColors = if (colors.size >= 2) colors else listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary
    )
    val effectiveColors = activeColors.map { if (enabled) it else it.copy(alpha = 0.45f) }
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (enabled) 0.56f else 0.22f),
            effectiveColors.last().copy(alpha = if (enabled) 0.65f else 0.25f)
        )
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, borderBrush),
        shadowElevation = if (enabled) 8.dp else 0.dp,
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .minimumInteractiveComponentSize()
            .then(modifier)
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(colors = effectiveColors))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (enabled) 0.22f else 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(
                LocalContentColor provides contentColor.copy(alpha = if (enabled) 1f else 0.65f)
            ) {
                ProvideTextStyle(
                    MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        content = content
                    )
                }
            }
        }
    }
}

@Composable
fun VaultGlassOutlinedButton(
    onClick: () -> Unit,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    shape: Shape = RoundedCornerShape(16.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    val vaultColors = LocalVaultColors.current
    val activeColors = if (colors.size >= 2) colors else listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary
    )
    val tintAlpha = if (vaultColors.isDark) 0.16f else 0.12f
    val borderBrush = Brush.linearGradient(
        colors = activeColors.map { it.copy(alpha = if (enabled) 0.72f else 0.30f) }
    )
    val fillBrush = Brush.linearGradient(
        colors = activeColors.map { it.copy(alpha = if (enabled) tintAlpha else 0.06f) }
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = vaultColors.glassSurface.copy(alpha = if (vaultColors.isDark) 0.55f else 0.72f),
        border = BorderStroke(1.2.dp, borderBrush),
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .minimumInteractiveComponentSize()
            .then(modifier)
    ) {
        Box(
            modifier = Modifier
                .background(fillBrush)
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(
                LocalContentColor provides contentColor.copy(alpha = if (enabled) 1f else 0.50f)
            ) {
                ProvideTextStyle(
                    MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        content = content
                    )
                }
            }
        }
    }
}

@Composable
fun VaultGlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    accentColors: List<Color> = LocalVaultColors.current.brandButtonGradient,
    trailingAction: (@Composable () -> Unit)? = null,
    testTag: String = "glass_search_bar"
) {
    val vaultColors = LocalVaultColors.current
    val activePalette = if (accentColors.size >= 2) accentColors else listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary
    )
    val isQueryActive = query.isNotBlank()
    val borderBrush = Brush.linearGradient(
        colors = if (isQueryActive) {
            activePalette.map { it.copy(alpha = 0.85f) }
        } else {
            listOf(
                vaultColors.glassHighlightBorder,
                activePalette[1].copy(alpha = if (vaultColors.isDark) 0.42f else 0.35f),
                activePalette.last().copy(alpha = if (vaultColors.isDark) 0.36f else 0.28f)
            )
        }
    )
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = vaultColors.glassSurface.copy(alpha = if (vaultColors.isDark) 0.72f else 0.86f),
        border = BorderStroke(if (isQueryActive) 1.4.dp else 1.dp, borderBrush),
        shadowElevation = if (isQueryActive) 8.dp else 4.dp
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = placeholder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            },
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = activePalette.map { it.copy(alpha = 0.22f) }
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = activePalette[1],
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("${testTag}_clear")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (trailingAction != null) {
                        trailingAction()
                    }
                }
            },
            singleLine = true,
            shape = shape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = activePalette[1]
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}


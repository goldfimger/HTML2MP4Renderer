package com.html2mp4.renderer.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.html2mp4.renderer.R
import com.html2mp4.renderer.ui.i18n.AppStrings
import com.html2mp4.renderer.ui.viewmodel.AppCacheStats
import com.html2mp4.renderer.util.DeviceProfile
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val GITHUB_REPO_URL = "https://github.com/goldfimger/HTML2MP4Renderer"
private const val GITHUB_PROFILE_URL = "https://github.com/goldfimger"

@Composable
fun AboutScreen(
    strings: AppStrings,
    deviceProfile: DeviceProfile,
    cacheStats: AppCacheStats,
    onRefreshCache: () -> Unit,
    onClearCache: () -> Unit
) {
    val context = LocalContext.current
    var showQrisDonateDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onRefreshCache() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (showQrisDonateDialog && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.blur(22.dp)
                } else {
                    Modifier
                }
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Minimalist App Identity & Offline Tagline Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        MinimalistAppEmblem(
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerTint = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    Text(
                        text = strings.appTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "v1.1.0 · Offline Engine",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = strings.aboutOfflineTagline,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 2. Developer & GitHub Card (3D Pixar / Monsters-Inc-inspired furry legged mascot)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = strings.aboutDeveloperLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Goldfimger",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "github.com/goldfimger/HTML2MP4Renderer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 3D Pixar-style cute light-gray one-horned rhino coding at Linux terminal desk
                    DeveloperCoderMascotStage()

                    Text(
                        text = strings.aboutCheckUpdateHeader,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL))
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        GitHubMarkIcon(
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.aboutGithubTitle,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    FilledTonalButton(
                        onClick = { showQrisDonateDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCode2,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.aboutDonateButton,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 3. Cache Monitoring (MB to GB) & Clear Cache Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = strings.aboutCacheTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = formatCacheMbGb(cacheStats.totalBytes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    val cacheRatio = (cacheStats.totalBytes.toFloat() / (1024f * 1024f * 1024f)).coerceIn(0.02f, 1f)
                    LinearProgressIndicator(
                        progress = { if (cacheStats.totalBytes == 0L) 0f else cacheRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    InfoRow(strings.aboutCacheWebViewLabel, formatCacheMbGb(cacheStats.webViewBytes))
                    InfoRow(strings.aboutCacheTempLabel, formatCacheMbGb(cacheStats.tempRenderBytes))
                    InfoRow(strings.aboutCacheTotalLabel, formatCacheMbGb(cacheStats.totalBytes))

                    FilledTonalButton(
                        onClick = {
                            onClearCache()
                            Toast.makeText(context, strings.aboutCacheClearedToast, Toast.LENGTH_SHORT).show()
                        },
                        enabled = !cacheStats.isClearing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.aboutClearCacheButton,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 4. Report Apps & Contact Developer (Goldfimger GitHub Profile)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = strings.aboutReportTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = strings.aboutReportBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_PROFILE_URL))
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        GitHubMarkIcon(
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.aboutContactDevButton,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 5. Special Thanks Card (Google AI Studio & GitHub)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = strings.aboutThanksTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = strings.aboutThanksBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = "Google AI Studio",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                GitHubMarkIcon(
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "GitHub",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 6. Multi-Chipset, Tablet & Chromebook (Googlebook) Compatibility Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Devices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = strings.aboutHardwareTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    InfoRow(strings.aboutChipsetLabel, deviceProfile.chipsetName)
                    InfoRow(
                        strings.aboutFormFactorLabel,
                        if (strings.isIndonesian) deviceProfile.formFactor.labelId else deviceProfile.formFactor.labelEn
                    )
                    InfoRow(strings.aboutEncoderLabel, deviceProfile.activeEncoderLabel)
                    InfoRow(strings.aboutRamLabel, "${deviceProfile.totalRamMb} MB")

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = strings.aboutSupportedDevices,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    if (showQrisDonateDialog) {
        QrisDonateLiquidGlassDialog(
            strings = strings,
            onDismiss = { showQrisDonateDialog = false }
        )
    }
}

@Composable
private fun QrisDonateLiquidGlassDialog(
    strings: AppStrings,
    onDismiss: () -> Unit
) {
    Dialog(
        // Strict lock: cannot be closed by outside tap or system back; ONLY the X button on the photo closes it
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        val dialogView = LocalView.current
        SideEffect {
            val win = (dialogView.parent as? DialogWindowProvider)?.window
            if (win != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                win.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                win.attributes = win.attributes.apply {
                    blurBehindRadius = 68
                }
            }
        }

        val glassTop = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        val glassBottom = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.34f)
        val scrimBase = Color.Black.copy(alpha = 0.38f)

        // Non-dismissible full-screen blur liquid-glass overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimBase)
                .background(Brush.verticalGradient(listOf(glassTop, glassBottom)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Intentionally does nothing; user MUST tap X on photo
                )
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.26f),
                                Color.White.copy(alpha = 0.12f)
                            )
                        )
                    )
                    .border(
                        width = 1.4.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.72f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.45f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // QRIS Photo with interactive X button overlaid directly on top-right corner of photo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.84f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.qris_donate),
                        contentDescription = strings.aboutQrisTitle,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.56f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = strings.closeButton,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MinimalistAppEmblem(
    tint: Color,
    containerTint: Color = Color.Transparent,
    modifier: Modifier = Modifier
) {
    val labelMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        val s = size.minDimension / 108f
        val cx = size.width / 2f

        // Golden Ratio content frame: 108 / 1.618 = 66.8 -> 20.6 proportional margins on all 4 sides
        val tagStyle = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = (23.5f * s).toSp(),
            color = tint,
            letterSpacing = (-0.3f * s).toSp()
        )

        val htmlLayout = labelMeasurer.measure(text = "html", style = tagStyle, softWrap = false, maxLines = 1)
        val mp4Layout = labelMeasurer.measure(text = "mp4", style = tagStyle, softWrap = false, maxLines = 1)

        // Centered "html" above "mp4" with symmetrical horizontal alignment
        drawText(
            textLayoutResult = htmlLayout,
            topLeft = Offset(cx - htmlLayout.size.width / 2f, 15.8f * s)
        )
        drawText(
            textLayoutResult = mp4Layout,
            topLeft = Offset(cx - mp4Layout.size.width / 2f, 37.8f * s)
        )

        // Smile-curved right-pointing silhouette arrow below "mp4"
        val smileArrow = Path().apply {
            moveTo(20.6f * s, 70.5f * s)
            cubicTo(20.6f * s, 69.3f * s, 22.0f * s, 68.9f * s, 23.0f * s, 69.8f * s)
            cubicTo(33.0f * s, 78.5f * s, 43.2f * s, 82.4f * s, 54.0f * s, 82.4f * s)
            cubicTo(63.4f * s, 82.4f * s, 71.2f * s, 79.4f * s, 78.6f * s, 72.8f * s)
            lineTo(77.6f * s, 69.4f * s)
            lineTo(87.4f * s, 72.0f * s)
            lineTo(81.0f * s, 79.6f * s)
            lineTo(79.8f * s, 76.5f * s)
            cubicTo(71.8f * s, 84.0f * s, 63.2f * s, 87.4f * s, 54.0f * s, 87.4f * s)
            cubicTo(41.8f * s, 87.4f * s, 30.8f * s, 82.4f * s, 21.0f * s, 71.8f * s)
            cubicTo(20.7f * s, 71.4f * s, 20.6f * s, 71.0f * s, 20.6f * s, 70.5f * s)
            close()
        }
        drawPath(smileArrow, color = tint)
    }
}

@Composable
fun GitHubMarkIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val s = size.minDimension / 24f
        val path = Path().apply {
            // Outer Octocat silhouette normalized to 24x24 grid
            moveTo(12f * s, 2f * s)
            cubicTo(6.48f * s, 2f * s, 2f * s, 6.48f * s, 2f * s, 12.02f * s)
            cubicTo(2f * s, 16.44f * s, 4.86f * s, 20.19f * s, 8.84f * s, 21.52f * s)
            cubicTo(9.34f * s, 21.61f * s, 9.52f * s, 21.30f * s, 9.52f * s, 21.03f * s)
            lineTo(9.52f * s, 19.32f * s)
            cubicTo(6.74f * s, 19.92f * s, 6.15f * s, 17.98f * s, 6.15f * s, 17.98f * s)
            cubicTo(5.70f * s, 16.82f * s, 5.04f * s, 16.51f * s, 5.04f * s, 16.51f * s)
            cubicTo(4.14f * s, 15.89f * s, 5.11f * s, 15.90f * s, 5.11f * s, 15.90f * s)
            cubicTo(6.11f * s, 15.97f * s, 6.64f * s, 16.93f * s, 6.64f * s, 16.93f * s)
            cubicTo(7.53f * s, 18.46f * s, 8.97f * s, 18.02f * s, 9.54f * s, 17.76f * s)
            cubicTo(9.63f * s, 17.11f * s, 9.89f * s, 16.67f * s, 10.17f * s, 16.42f * s)
            cubicTo(7.95f * s, 16.17f * s, 5.62f * s, 15.31f * s, 5.62f * s, 11.47f * s)
            cubicTo(5.62f * s, 10.38f * s, 6.01f * s, 9.48f * s, 6.65f * s, 8.78f * s)
            cubicTo(6.55f * s, 8.53f * s, 6.20f * s, 7.50f * s, 6.75f * s, 6.12f * s)
            cubicTo(6.75f * s, 6.12f * s, 7.59f * s, 5.85f * s, 9.50f * s, 7.14f * s)
            cubicTo(10.30f * s, 6.92f * s, 11.15f * s, 6.81f * s, 12.00f * s, 6.81f * s)
            cubicTo(12.85f * s, 6.81f * s, 13.70f * s, 6.92f * s, 14.50f * s, 7.14f * s)
            cubicTo(16.41f * s, 5.85f * s, 17.25f * s, 6.12f * s, 17.25f * s, 6.12f * s)
            cubicTo(17.80f * s, 7.50f * s, 17.45f * s, 8.53f * s, 17.35f * s, 8.78f * s)
            cubicTo(17.99f * s, 9.48f * s, 18.38f * s, 10.38f * s, 18.38f * s, 11.47f * s)
            cubicTo(18.38f * s, 15.32f * s, 16.04f * s, 16.17f * s, 13.81f * s, 16.42f * s)
            cubicTo(14.17f * s, 16.73f * s, 14.48f * s, 17.34f * s, 14.48f * s, 18.28f * s)
            lineTo(14.48f * s, 21.03f * s)
            cubicTo(14.48f * s, 21.30f * s, 14.66f * s, 21.62f * s, 15.17f * s, 21.52f * s)
            cubicTo(19.14f * s, 20.19f * s, 22f * s, 16.44f * s, 22f * s, 12.02f * s)
            cubicTo(22f * s, 6.48f * s, 17.52f * s, 2f * s, 12f * s, 2f * s)
            close()
        }
        drawPath(path, color = tint)
    }
}

private fun formatCacheMbGb(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.2f MB", mb)
    }
}

@Composable
private fun DeveloperCoderMascotStage() {
    // 1f = turned back facing user & greeting, 0f = swiveled facing Linux monitor & typing
    val lookBackProgress = remember { Animatable(1f) }
    var isGreeting by remember { mutableStateOf(true) }
    var replayTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(replayTrigger) {
        isGreeting = true
        lookBackProgress.snapTo(0.12f)
        lookBackProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
        )
        delay(2400L)
        isGreeting = false
        lookBackProgress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing)
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mascotStage3d")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave"
    )
    val typePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "typing"
    )
    val terminalTick by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "terminal"
    )
    val blinkCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )

    val textMeasurer = rememberTextMeasurer()
    val stageTop = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
    val stageFloor = MaterialTheme.colorScheme.surfaceContainerLow
    val accentPrimary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(194.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(stageTop, stageFloor)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { replayTrigger++ }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val turn = lookBackProgress.value
            val isBlinking = blinkCycle > 0.93f

            // Studio 3D floor & ambient occlusion shadows
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(w * 0.30f, h * 0.89f),
                    radius = w * 0.20f
                ),
                topLeft = Offset(w * 0.12f, h * 0.83f),
                size = Size(w * 0.36f, h * 0.12f)
            )
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(w * 0.70f, h * 0.89f),
                    radius = w * 0.28f
                ),
                topLeft = Offset(w * 0.43f, h * 0.83f),
                size = Size(w * 0.52f, h * 0.12f)
            )

            // --- 3D DESK & LINUX WORKSTATION (Right Side) ---
            val deskTopY = h * 0.61f
            val deskLeft = w * 0.45f
            val deskRight = w * 0.95f

            // 3D Desk legs with metallic vertical gradient
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color(0xFF64748B), Color(0xFF334155), Color(0xFF1E293B))),
                topLeft = Offset(deskLeft + w * 0.03f, deskTopY + h * 0.05f),
                size = Size(w * 0.025f, h * 0.25f),
                cornerRadius = CornerRadius(5f, 5f)
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color(0xFF64748B), Color(0xFF334155), Color(0xFF1E293B))),
                topLeft = Offset(deskRight - w * 0.055f, deskTopY + h * 0.05f),
                size = Size(w * 0.025f, h * 0.25f),
                cornerRadius = CornerRadius(5f, 5f)
            )
            // 3D Beveled Desk Surface
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF64748B), Color(0xFF334155), Color(0xFF1E293B))),
                topLeft = Offset(deskLeft, deskTopY),
                size = Size(deskRight - deskLeft, h * 0.065f),
                cornerRadius = CornerRadius(10f, 10f)
            )
            // Subtle Linux monitor glow cast onto desk
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF34D399).copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(w * 0.72f, deskTopY + 4f),
                    radius = w * 0.18f
                ),
                topLeft = Offset(w * 0.54f, deskTopY - 4f),
                size = Size(w * 0.36f, h * 0.06f)
            )

            // 3D Aluminum Monitor Stand
            val monCenterX = w * 0.74f
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color(0xFFCBD5E1), Color(0xFF64748B), Color(0xFF334155))),
                topLeft = Offset(monCenterX - w * 0.018f, deskTopY - h * 0.09f),
                size = Size(w * 0.036f, h * 0.095f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF94A3B8), Color(0xFF334155))),
                topLeft = Offset(monCenterX - w * 0.075f, deskTopY - h * 0.02f),
                size = Size(w * 0.15f, h * 0.026f),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // 3D Curved Widescreen Monitor + Linux Terminal
            val monW = w * 0.35f
            val monH = h * 0.42f
            val monX = monCenterX - monW / 2f
            val monY = deskTopY - h * 0.08f - monH
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF334155), Color(0xFF0F172A)),
                    start = Offset(monX, monY),
                    end = Offset(monX + monW, monY + monH)
                ),
                topLeft = Offset(monX, monY),
                size = Size(monW, monH),
                cornerRadius = CornerRadius(14f, 14f)
            )
            val scrX = monX + 6f
            val scrY = monY + 6f
            val scrW = monW - 12f
            val scrH = monH - 14f
            drawRoundRect(
                color = Color(0xFF080E1A),
                topLeft = Offset(scrX, scrY),
                size = Size(scrW, scrH),
                cornerRadius = CornerRadius(8f, 8f)
            )
            // Linux Terminal Window Controls
            drawCircle(Color(0xFFEF4444), radius = 2.6f, center = Offset(scrX + 8f, scrY + 7f))
            drawCircle(Color(0xFFF59E0B), radius = 2.6f, center = Offset(scrX + 16f, scrY + 7f))
            drawCircle(Color(0xFF10B981), radius = 2.6f, center = Offset(scrX + 24f, scrY + 7f))

            val termPrompt = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 6.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
            val termCode = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 5.5.sp, color = Color(0xFF93C5FD))
            drawText(textMeasurer, "dev@linux:~$", Offset(scrX + 6f, scrY + 13f), termPrompt, softWrap = false, maxLines = 1)
            val buildLines = listOf(
                "./gradlew build",
                "> compileKotlin",
                "> muxer MP4 OK",
                "[APP] 3D Engine OK"
            )
            val lineCount = if (turn > 0.45f) 3 else (2 + ((terminalTick * 3f).toInt() % 3)).coerceAtMost(4)
            for (i in 0 until lineCount) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = buildLines[i],
                    topLeft = Offset(scrX + 6f, scrY + 23f + i * 9.5f),
                    style = if (i >= 2) termPrompt else termCode,
                    softWrap = false,
                    maxLines = 1
                )
            }
            if (terminalTick % 0.5f < 0.28f) {
                drawRect(Color(0xFF34D399), Offset(scrX + scrW * 0.62f, scrY + 24f + (lineCount - 1) * 9.5f), Size(5f, 7.5f))
            }

            // 3D Mechanical Keyboard & Mouse
            val kbX = deskLeft + w * 0.04f
            val kbY = deskTopY - h * 0.028f
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF0F172A))),
                topLeft = Offset(kbX, kbY),
                size = Size(w * 0.175f, h * 0.03f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            for (k in 0..5) {
                drawRoundRect(
                    color = accentPrimary.copy(alpha = 0.8f),
                    topLeft = Offset(kbX + 5f + k * (w * 0.025f), kbY + 2f),
                    size = Size(w * 0.018f, 3f),
                    cornerRadius = CornerRadius(1.5f, 1.5f)
                )
            }
            val mouseX = deskLeft + w * 0.245f
            drawRoundRect(Color(0xFF0F172A), Offset(mouseX - 4f, deskTopY - 2f), Size(w * 0.09f, 4f), CornerRadius(2f, 2f))
            val mouseNudge = if (turn < 0.35f) sin(typePhase) * 2.2f else 0f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color(0xFF94A3B8)),
                    center = Offset(mouseX + mouseNudge + w * 0.02f, deskTopY - h * 0.022f),
                    radius = w * 0.045f
                ),
                topLeft = Offset(mouseX + mouseNudge, deskTopY - h * 0.032f),
                size = Size(w * 0.052f, h * 0.032f)
            )

            // --- 3D SWIVEL CHAIR ---
            val chairX = w * 0.29f
            val chairBaseY = h * 0.87f
            val seatY = h * 0.66f
            // Chrome cylinder stem
            drawLine(
                brush = Brush.horizontalGradient(listOf(Color(0xFFCBD5E1), Color(0xFF475569))),
                start = Offset(chairX, seatY + h * 0.03f),
                end = Offset(chairX, chairBaseY - 4f),
                strokeWidth = 7f,
                cap = StrokeCap.Round
            )
            // 5-star caster legs
            drawLine(Color(0xFF334155), Offset(chairX - w * 0.10f, chairBaseY), Offset(chairX + w * 0.10f, chairBaseY), 6f, StrokeCap.Round)
            drawCircle(Color(0xFF0F172A), 5.2f, Offset(chairX - w * 0.10f, chairBaseY + 3f))
            drawCircle(Color(0xFF0F172A), 5.2f, Offset(chairX, chairBaseY + 4f))
            drawCircle(Color(0xFF0F172A), 5.2f, Offset(chairX + w * 0.10f, chairBaseY + 3f))

            // 3D Swiveling Backrest
            val chairBackOffsetX = -w * 0.095f * (1f - turn * 0.88f)
            val chairBackW = w * 0.046f + (w * 0.10f * turn)
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF475569), Color(0xFF1E293B), Color(0xFF0F172A)),
                    center = Offset(chairX + chairBackOffsetX, h * 0.50f),
                    radius = w * 0.12f
                ),
                topLeft = Offset(chairX + chairBackOffsetX - chairBackW / 2f, h * 0.39f),
                size = Size(chairBackW, h * 0.27f),
                cornerRadius = CornerRadius(16f, 16f)
            )
            // Sculpted 3D Seat Cushion
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF64748B), Color(0xFF1E293B))),
                topLeft = Offset(chairX - w * 0.10f, seatY),
                size = Size(w * 0.20f, h * 0.052f),
                cornerRadius = CornerRadius(12f, 12f)
            )

            // --- CUTE ORANGE STRIPED TABBY CAT (KUCING ORANGE BELANG) IN 3D PIXAR STYLE ---
            val bobY = if (turn > 0.5f) sin(wavePhase * 1.4f) * 1.8f else sin(typePhase * 2f) * 2.0f
            val bodyCx = chairX + (1f - turn) * w * 0.015f
            val bodyCy = h * 0.54f + bobY

            val catOrangeLight = Color(0xFFFED7AA)
            val catOrangeMid = Color(0xFFFB923C)
            val catOrangeShade = Color(0xFFEA580C)
            val catStripe = Color(0xFFC2410C)
            val catCream = Color(0xFFFFF7ED)
            val catEarPink = Color(0xFFFDA4AF)

            // Cute Curling Orange Striped Tail
            val tailSway = sin(wavePhase * 1.6f) * 5f
            val tailPath = Path().apply {
                moveTo(bodyCx - w * 0.085f, bodyCy + h * 0.09f)
                cubicTo(
                    bodyCx - w * 0.155f, bodyCy + h * 0.06f,
                    bodyCx - w * 0.175f, bodyCy - h * 0.06f + tailSway,
                    bodyCx - w * 0.135f, bodyCy - h * 0.11f + tailSway
                )
            }
            drawPath(
                path = tailPath,
                brush = Brush.verticalGradient(listOf(catOrangeLight, catOrangeMid, catOrangeShade)),
                style = Stroke(width = 11f, cap = StrokeCap.Round)
            )
            // Tail tabby stripes
            drawCircle(catStripe, radius = 3.6f, center = Offset(bodyCx - w * 0.145f, bodyCy - h * 0.02f + tailSway * 0.5f))
            drawCircle(catStripe, radius = 3.2f, center = Offset(bodyCx - w * 0.140f, bodyCy - h * 0.08f + tailSway * 0.85f))

            // Chubby Striped Orange Cat Legs & Cream Paws
            val lFootSwing = sin(typePhase) * (if (turn < 0.4f) 3.5f else 1.5f)
            val rFootSwing = cos(typePhase) * (if (turn < 0.4f) 3.5f else 1.5f)
            val lLegX = bodyCx - w * 0.044f + (1f - turn) * w * 0.01f
            val rLegX = bodyCx + w * 0.044f + (1f - turn) * w * 0.02f
            val footY = h * 0.79f

            // Left Leg & Paw
            drawLine(
                brush = Brush.horizontalGradient(listOf(catOrangeLight, catOrangeMid, catOrangeShade)),
                start = Offset(lLegX, seatY + h * 0.02f),
                end = Offset(lLegX - 2f, footY + lFootSwing),
                strokeWidth = w * 0.050f,
                cap = StrokeCap.Round
            )
            drawLine(catStripe, Offset(lLegX - 6f, seatY + h * 0.06f), Offset(lLegX + 4f, seatY + h * 0.06f), 3.2f, StrokeCap.Round)
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, catCream, Color(0xFFFED7AA)),
                    center = Offset(lLegX, footY + lFootSwing - 2f),
                    radius = w * 0.046f
                ),
                topLeft = Offset(lLegX - w * 0.034f, footY + lFootSwing - h * 0.01f),
                size = Size(w * 0.068f, h * 0.054f)
            )
            drawCircle(catEarPink, radius = 2.4f, center = Offset(lLegX - 4f, footY + lFootSwing + h * 0.014f))
            drawCircle(catEarPink, radius = 2.4f, center = Offset(lLegX + 4f, footY + lFootSwing + h * 0.014f))

            // Right Leg & Paw
            drawLine(
                brush = Brush.horizontalGradient(listOf(catOrangeLight, catOrangeMid, catOrangeShade)),
                start = Offset(rLegX, seatY + h * 0.02f),
                end = Offset(rLegX + 4f, footY + rFootSwing),
                strokeWidth = w * 0.050f,
                cap = StrokeCap.Round
            )
            drawLine(catStripe, Offset(rLegX - 4f, seatY + h * 0.06f), Offset(rLegX + 6f, seatY + h * 0.06f), 3.2f, StrokeCap.Round)
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, catCream, Color(0xFFFED7AA)),
                    center = Offset(rLegX + 4f, footY + rFootSwing - 2f),
                    radius = w * 0.046f
                ),
                topLeft = Offset(rLegX - w * 0.026f, footY + rFootSwing - h * 0.01f),
                size = Size(w * 0.070f, h * 0.054f)
            )
            drawCircle(catEarPink, radius = 2.4f, center = Offset(rLegX, footY + rFootSwing + h * 0.014f))
            drawCircle(catEarPink, radius = 2.4f, center = Offset(rLegX + 8f, footY + rFootSwing + h * 0.014f))

            // Chubby Gemoy 3D Orange Cat Torso + Cream Chest Bib + Tabby Stripes
            val bodyRx = w * 0.110f
            val bodyRy = h * 0.145f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(catOrangeLight, catOrangeMid, catOrangeShade),
                    center = Offset(bodyCx - bodyRx * 0.25f, bodyCy - bodyRy * 0.22f),
                    radius = bodyRx * 1.45f
                ),
                topLeft = Offset(bodyCx - bodyRx, bodyCy - bodyRy * 0.55f),
                size = Size(bodyRx * 2f, bodyRy * 1.65f)
            )
            // Body tabby side stripes
            drawLine(catStripe, Offset(bodyCx - bodyRx * 0.85f, bodyCy), Offset(bodyCx - bodyRx * 0.52f, bodyCy + 3f), 3.8f, StrokeCap.Round)
            drawLine(catStripe, Offset(bodyCx - bodyRx * 0.82f, bodyCy + 11f), Offset(bodyCx - bodyRx * 0.48f, bodyCy + 13f), 3.8f, StrokeCap.Round)
            drawLine(catStripe, Offset(bodyCx + bodyRx * 0.52f, bodyCy + 3f), Offset(bodyCx + bodyRx * 0.85f, bodyCy), 3.8f, StrokeCap.Round)
            // Cream belly chest bib
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, catCream),
                    center = Offset(bodyCx + (1f - turn) * 4f, bodyCy + 4f),
                    radius = bodyRx * 0.7f
                ),
                topLeft = Offset(bodyCx - bodyRx * 0.56f + (1f - turn) * 4f, bodyCy - bodyRy * 0.22f),
                size = Size(bodyRx * 1.12f, bodyRy * 1.16f)
            )

            // Cute 3D Orange Cat Head + Triangular Pointy Ears
            val headCx = bodyCx + (1f - turn) * w * 0.015f
            val headCy = h * 0.34f + bobY
            val headRx = w * 0.118f
            val headRy = h * 0.145f

            val lEarTipX = headCx - headRx * 0.78f
            val lEarTipY = headCy - headRy * 1.38f
            val rEarTipX = headCx + headRx * 0.78f
            val rEarTipY = headCy - headRy * 1.38f

            // Left Pointy Cat Ear + Pink Inner Ear
            val lEarOuter = Path().apply {
                moveTo(headCx - headRx * 0.90f, headCy - headRy * 0.35f)
                quadraticTo(headCx - headRx * 0.95f, lEarTipY + 6f, lEarTipX, lEarTipY)
                quadraticTo(headCx - headRx * 0.35f, headCy - headRy * 0.98f, headCx - headRx * 0.25f, headCy - headRy * 0.82f)
                close()
            }
            drawPath(lEarOuter, brush = Brush.verticalGradient(listOf(catOrangeLight, catOrangeMid)))
            val lEarInner = Path().apply {
                moveTo(headCx - headRx * 0.76f, headCy - headRy * 0.45f)
                lineTo(lEarTipX + 2f, lEarTipY + 6f)
                lineTo(headCx - headRx * 0.38f, headCy - headRy * 0.78f)
                close()
            }
            drawPath(lEarInner, color = catEarPink.copy(alpha = 0.85f))

            // Right Pointy Cat Ear + Pink Inner Ear
            val rEarOuter = Path().apply {
                moveTo(headCx + headRx * 0.90f, headCy - headRy * 0.35f)
                quadraticTo(headCx + headRx * 0.95f, rEarTipY + 6f, rEarTipX, rEarTipY)
                quadraticTo(headCx + headRx * 0.35f, headCy - headRy * 0.98f, headCx + headRx * 0.25f, headCy - headRy * 0.82f)
                close()
            }
            drawPath(rEarOuter, brush = Brush.verticalGradient(listOf(catOrangeLight, catOrangeMid)))
            val rEarInner = Path().apply {
                moveTo(headCx + headRx * 0.76f, headCy - headRy * 0.45f)
                lineTo(rEarTipX - 2f, rEarTipY + 6f)
                lineTo(headCx + headRx * 0.38f, headCy - headRy * 0.78f)
                close()
            }
            drawPath(rEarInner, color = catEarPink.copy(alpha = 0.85f))

            // Sculpted 3D Orange Cat Head Sphere
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(catOrangeLight, catOrangeMid, catOrangeShade),
                    center = Offset(headCx - headRx * 0.22f, headCy - headRy * 0.28f),
                    radius = headRx * 1.38f
                ),
                topLeft = Offset(headCx - headRx, headCy - headRy),
                size = Size(headRx * 2f, headRy * 2f)
            )

            // Iconic Forehead & Cheek Tabby Stripes ("Belang Kucing Oyen")
            val faceCenterX = headCx + (1f - turn) * headRx * 0.24f
            drawLine(catStripe, Offset(faceCenterX, headCy - headRy * 0.85f), Offset(faceCenterX, headCy - headRy * 0.48f), 3.4f, StrokeCap.Round)
            drawLine(catStripe, Offset(faceCenterX - 7f, headCy - headRy * 0.80f), Offset(faceCenterX - 5f, headCy - headRy * 0.50f), 2.8f, StrokeCap.Round)
            drawLine(catStripe, Offset(faceCenterX + 7f, headCy - headRy * 0.80f), Offset(faceCenterX + 5f, headCy - headRy * 0.50f), 2.8f, StrokeCap.Round)
            // Cheek stripes
            drawLine(catStripe, Offset(headCx - headRx * 0.88f, headCy + 2f), Offset(headCx - headRx * 0.62f, headCy + 4f), 2.8f, StrokeCap.Round)
            drawLine(catStripe, Offset(headCx + headRx * 0.62f, headCy + 4f), Offset(headCx + headRx * 0.88f, headCy + 2f), 2.8f, StrokeCap.Round)

            // Soft Cream Cat Muzzle Puffs + Cute Pink Triangle Nose + ":3" Cat Smile
            val faceCy = headCy + headRy * 0.20f
            val eyeSpread = headRx * (0.40f + turn * 0.08f)
            val eyeCy = headCy - headRy * 0.14f

            drawOval(
                color = catCream,
                topLeft = Offset(faceCenterX - headRx * 0.34f, faceCy - 2f),
                size = Size(headRx * 0.35f, headRy * 0.36f)
            )
            drawOval(
                color = catCream,
                topLeft = Offset(faceCenterX - headRx * 0.01f, faceCy - 2f),
                size = Size(headRx * 0.35f, headRy * 0.36f)
            )

            // Cute Pink Triangle Cat Nose
            val nosePath = Path().apply {
                moveTo(faceCenterX - 4f, faceCy - 1f)
                lineTo(faceCenterX + 4f, faceCy - 1f)
                lineTo(faceCenterX, faceCy + 4f)
                close()
            }
            drawPath(nosePath, color = Color(0xFFF43F5E))

            // ":3" Cat Mouth Smile
            val catMouthLeft = Path().apply {
                moveTo(faceCenterX - 8f, faceCy + 5f)
                quadraticTo(faceCenterX - 4f, faceCy + 10f, faceCenterX, faceCy + 5f)
            }
            val catMouthRight = Path().apply {
                moveTo(faceCenterX, faceCy + 5f)
                quadraticTo(faceCenterX + 4f, faceCy + 10f, faceCenterX + 8f, faceCy + 5f)
            }
            drawPath(catMouthLeft, Color(0xFF7C2D12), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
            drawPath(catMouthRight, Color(0xFF7C2D12), style = Stroke(width = 2.2f, cap = StrokeCap.Round))

            // Cute Whiskers
            drawLine(Color(0xFF7C2D12).copy(alpha = 0.65f), Offset(faceCenterX - 11f, faceCy + 2f), Offset(faceCenterX - 24f, faceCy - 1f), 1.4f, StrokeCap.Round)
            drawLine(Color(0xFF7C2D12).copy(alpha = 0.65f), Offset(faceCenterX - 11f, faceCy + 5f), Offset(faceCenterX - 24f, faceCy + 7f), 1.4f, StrokeCap.Round)
            drawLine(Color(0xFF7C2D12).copy(alpha = 0.65f), Offset(faceCenterX + 11f, faceCy + 2f), Offset(faceCenterX + 24f, faceCy - 1f), 1.4f, StrokeCap.Round)
            drawLine(Color(0xFF7C2D12).copy(alpha = 0.65f), Offset(faceCenterX + 11f, faceCy + 5f), Offset(faceCenterX + 24f, faceCy + 7f), 1.4f, StrokeCap.Round)

            // Soft 3D Rosy Cheeks
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFB7185).copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(faceCenterX - eyeSpread - 2f, faceCy + 2f),
                    radius = 10f
                ),
                topLeft = Offset(faceCenterX - eyeSpread - 8f, faceCy - 2f),
                size = Size(13f, 9f)
            )
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFB7185).copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(faceCenterX + eyeSpread + 2f, faceCy + 2f),
                    radius = 10f
                ),
                topLeft = Offset(faceCenterX + eyeSpread - 5f, faceCy - 2f),
                size = Size(13f, 9f)
            )

            // Giant Glossy 3D Pixar Cat Eyes
            val gazeShiftX = if (turn > 0.45f) 0f else 2.0f
            listOf(faceCenterX - eyeSpread, faceCenterX + eyeSpread).forEach { ex ->
                if (isBlinking) {
                    drawLine(Color(0xFF451A03), Offset(ex - 5f, eyeCy), Offset(ex + 5f, eyeCy), 2.8f, StrokeCap.Round)
                } else {
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF451A03), Color(0xFF1C1917), Color.Black),
                            center = Offset(ex + gazeShiftX * 0.4f, eyeCy + 1.5f),
                            radius = 8.5f
                        ),
                        topLeft = Offset(ex - 5.2f, eyeCy - 6.2f),
                        size = Size(10.4f, 12.4f)
                    )
                    drawOval(
                        color = Color(0xFFF59E0B).copy(alpha = 0.42f),
                        topLeft = Offset(ex - 2.8f, eyeCy + 2.2f),
                        size = Size(5.6f, 2.8f)
                    )
                    drawCircle(Color.White, radius = 2.4f, center = Offset(ex - 1.5f + gazeShiftX * 0.5f, eyeCy - 2.8f))
                    drawCircle(Color.White, radius = 1.1f, center = Offset(ex + 2.0f + gazeShiftX * 0.5f, eyeCy + 1.2f))
                }
            }

            // 3D Headset + Metallic Gold Ring + Boom Microphone
            val bandPath = Path().apply {
                moveTo(headCx - headRx * 0.94f, headCy)
                cubicTo(
                    headCx - headRx * 0.94f, headCy - headRy * 1.24f,
                    headCx + headRx * 0.94f, headCy - headRy * 1.24f,
                    headCx + headRx * 0.94f, headCy
                )
            }
            drawPath(
                path = bandPath,
                brush = Brush.verticalGradient(listOf(Color(0xFF475569), Color(0xFF0F172A))),
                style = Stroke(width = 5.4f, cap = StrokeCap.Round)
            )
            val leftCupX = headCx - headRx * 0.96f
            val rightCupX = headCx + headRx * 0.96f
            listOf(leftCupX, rightCupX).forEach { cx ->
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF475569), Color(0xFF0F172A)),
                        center = Offset(cx - 1.5f, headCy - 2f),
                        radius = 12f
                    ),
                    topLeft = Offset(cx - 6f, headCy - 9f),
                    size = Size(12f, 20f)
                )
                drawOval(Color(0xFFF59E0B), Offset(cx - 3.8f, headCy - 6.2f), Size(7.6f, 14f), style = Stroke(1.8f))
            }
            val micTipX = faceCenterX - headRx * 0.30f
            val micTipY = faceCy + 6f
            val micPath = Path().apply {
                moveTo(leftCupX, headCy + 7f)
                quadraticTo(leftCupX + 5f, micTipY + 4f, micTipX, micTipY)
            }
            drawPath(micPath, Color(0xFF0F172A), style = Stroke(width = 2.6f, cap = StrokeCap.Round))
            drawCircle(Color(0xFF1E293B), radius = 4f, center = Offset(micTipX, micTipY))
            drawCircle(Color(0xFFFDE047), radius = 1.4f, center = Offset(micTipX, micTipY - 0.6f))

            // 3D Orange Cat Arms + Gold Gloves (Waving on Hello There!, typing on keyboard!)
            val leftTapY = if (turn < 0.4f) sin(typePhase) * 4.5f else 0f
            val rightTapY = if (turn < 0.4f) cos(typePhase) * 4.5f else 0f

            val lHandX = bodyCx - bodyRx * 0.65f + (1f - turn) * w * 0.135f
            val lHandY = (h * 0.61f) * turn + (kbY - 3f + leftTapY) * (1f - turn)
            drawLine(
                brush = Brush.horizontalGradient(listOf(catOrangeLight, catOrangeMid)),
                start = Offset(bodyCx - bodyRx * 0.55f, bodyCy + 2f),
                end = Offset(lHandX, lHandY),
                strokeWidth = 12.5f,
                cap = StrokeCap.Round
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFF59E0B), Color(0xFF92400E)),
                    center = Offset(lHandX - 2f, lHandY - 2f),
                    radius = 9f
                ),
                radius = 7.8f,
                center = Offset(lHandX, lHandY)
            )

            val rShoulderX = bodyCx + bodyRx * 0.65f
            val rShoulderY = bodyCy + 2f
            val wavePivotX = bodyCx + bodyRx * 1.15f
            val wavePivotY = headCy + headRy * 0.25f
            val rCodeX = kbX + w * 0.088f
            val rCodeY = kbY - 4f + rightTapY

            val rHandX = rCodeX * (1f - turn) + (wavePivotX + wavePhase * 8f) * turn
            val rHandY = rCodeY * (1f - turn) + (wavePivotY - 8f + sin(wavePhase.toDouble()).toFloat() * 3f) * turn

            drawLine(
                brush = Brush.horizontalGradient(listOf(catOrangeLight, catOrangeMid)),
                start = Offset(rShoulderX, rShoulderY),
                end = Offset(rHandX, rHandY),
                strokeWidth = 12.5f,
                cap = StrokeCap.Round
            )
            rotate(degrees = if (turn > 0.4f) wavePhase * 24f else 0f, pivot = Offset(rHandX, rHandY)) {
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFEF08A), Color(0xFFF59E0B), Color(0xFF92400E)),
                        center = Offset(rHandX - 2f, rHandY - 3f),
                        radius = 11f
                    ),
                    topLeft = Offset(rHandX - 8.2f, rHandY - 9.2f),
                    size = Size(16.4f, 18.4f)
                )
                drawCircle(Color(0xFFF59E0B), radius = 3.6f, center = Offset(rHandX - 6.2f, rHandY - 1f))
            }
        }

        // Speech bubble: "Hello There!"
        AnimatedVisibility(
            visible = isGreeting,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 3.dp
            ) {
                Text(
                    text = "Hello There!",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                )
            }
        }
    }
}

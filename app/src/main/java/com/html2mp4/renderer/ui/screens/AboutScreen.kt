package com.html2mp4.renderer.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.html2mp4.renderer.ui.i18n.AppStrings
import com.html2mp4.renderer.util.DeviceProfile

private const val GITHUB_REPO_URL = "https://github.com/goldfimger/HTML2MP4Renderer"

@Composable
fun AboutScreen(
    strings: AppStrings,
    deviceProfile: DeviceProfile
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                            modifier = Modifier.size(38.dp)
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

            // 2. Developer & GitHub Card
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
                }
            }

            // 3. Special Thanks Card (Google AI Studio & GitHub)
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

            // 4. Multi-Chipset, Tablet & Chromebook (Googlebook) Compatibility Card
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
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val leftBracket = Path().apply {
            moveTo(w * 0.30f, h * 0.24f)
            lineTo(w * 0.12f, h * 0.50f)
            lineTo(w * 0.30f, h * 0.76f)
        }
        drawPath(leftBracket, color = tint, style = stroke)

        val rightBracket = Path().apply {
            moveTo(w * 0.70f, h * 0.24f)
            lineTo(w * 0.88f, h * 0.50f)
            lineTo(w * 0.70f, h * 0.76f)
        }
        drawPath(rightBracket, color = tint, style = stroke)

        val playTri = Path().apply {
            moveTo(w * 0.42f, h * 0.30f)
            lineTo(w * 0.65f, h * 0.50f)
            lineTo(w * 0.42f, h * 0.70f)
            close()
        }
        drawPath(playTri, color = tint)
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

package com.kcpd.myfolder.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kcpd.myfolder.MainActivity
import com.kcpd.myfolder.R

/**
 * Home screen widget for quick capture of photos, videos, and audio.
 * Provides 3 action buttons that deep-link to the respective capture screens.
 */
class QuickCaptureWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent(context)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        // Use Compose Color for ColorProvider
        val backgroundColor = ColorProvider(Color(0xFF1A1A2E))
        val textColor = ColorProvider(Color.White)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(backgroundColor)
                .cornerRadius(16.dp)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "Quick Capture",
                style = TextStyle(
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Action buttons row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Photo button
                CaptureButton(
                    context = context,
                    icon = R.drawable.ic_widget_camera,
                    label = "Photo",
                    captureType = "photo"
                )

                Spacer(modifier = GlanceModifier.width(12.dp))

                // Video button
                CaptureButton(
                    context = context,
                    icon = R.drawable.ic_widget_video,
                    label = "Video",
                    captureType = "video"
                )

                Spacer(modifier = GlanceModifier.width(12.dp))

                // Audio button
                CaptureButton(
                    context = context,
                    icon = R.drawable.ic_widget_audio,
                    label = "Audio",
                    captureType = "audio"
                )
            }
        }
    }

    @Composable
    private fun CaptureButton(
        context: Context,
        icon: Int,
        label: String,
        captureType: String
    ) {
        val buttonColor = ColorProvider(Color(0xFF2D2D44))
        val textColor = ColorProvider(Color.White)

        // Create intent for deep link
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("myfolder://capture/$captureType")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        Column(
            modifier = GlanceModifier
                .background(buttonColor)
                .cornerRadius(12.dp)
                .padding(12.dp)
                .clickable(actionStartActivity(intent)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = label,
                modifier = GlanceModifier.size(28.dp)
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = label,
                style = TextStyle(
                    color = textColor,
                    fontSize = 11.sp
                )
            )
        }
    }
}

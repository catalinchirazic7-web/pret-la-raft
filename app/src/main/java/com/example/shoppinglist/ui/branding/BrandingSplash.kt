package com.example.shoppinglist.ui.branding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.shoppinglist.ui.common.AppBackground
import kotlinx.coroutines.delay

/** Afișează progresul doar dacă importul depășește acest interval. */
const val PROGRESS_DELAY_MS = 400L

private val LidlBlue = Color(0xFF0050AA)
private val PennyRed = Color(0xFFCC0000)

/** Ecranul de branding „PrețLaRaft”, afișat cât timp catalogul se importă. */
@Composable
fun BrandingSplash(modifier: Modifier = Modifier) {
    var showProgress by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(PROGRESS_DELAY_MS)
        showProgress = true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandEmblem(modifier = Modifier.size(112.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "PrețLaRaft",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Oferte Lidl & Penny | Scaner de preț",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(40.dp))
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = LidlBlue,
                )
            }
        }
    }
}

/** Emblemă minimalistă: o etichetă de preț cu accent albastru (Lidl) și roșu (Penny). */
@Composable
private fun BrandEmblem(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(w * 0.2f)

        drawRoundRect(
            color = Color.White,
            size = size,
            cornerRadius = corner,
        )
        drawRoundRect(
            color = LidlBlue,
            topLeft = Offset(w * 0.2f, h * 0.24f),
            size = Size(w * 0.6f, h * 0.2f),
            cornerRadius = CornerRadius(h * 0.1f),
        )
        drawRoundRect(
            color = PennyRed,
            topLeft = Offset(w * 0.2f, h * 0.56f),
            size = Size(w * 0.42f, h * 0.2f),
            cornerRadius = CornerRadius(h * 0.1f),
        )
        drawCircle(
            color = PennyRed,
            radius = w * 0.07f,
            center = Offset(w * 0.78f, h * 0.66f),
        )
    }
}

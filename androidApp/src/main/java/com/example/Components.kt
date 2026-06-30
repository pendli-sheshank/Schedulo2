package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SkeletalLoader(modifier: Modifier = Modifier) {
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by shimmer.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerAnimation"
    )

    val shimmerColor = Color.White.copy(alpha = 0.1f)

    Column(modifier = modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(3) {
            SkeletalCard(shimmerX)
        }
    }
}

@Composable
private fun SkeletalCard(shimmerX: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title shimmer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    brush = shimmerBrush(shimmerX, MaterialTheme.colorScheme.surfaceVariant)
                )
        )

        // Two smaller text shimmers
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        brush = shimmerBrush(shimmerX, MaterialTheme.colorScheme.surfaceVariant)
                    )
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        brush = shimmerBrush(shimmerX, MaterialTheme.colorScheme.surfaceVariant)
                    )
            )
        }

        // Bottom shimmer line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    brush = shimmerBrush(shimmerX, MaterialTheme.colorScheme.surfaceVariant)
                )
        )
    }
}

@Composable
private fun shimmerBrush(
    shimmerX: Float,
    baseColor: Color
) = Brush.linearGradient(
    colors = listOf(
        baseColor.copy(alpha = 0.5f),
        baseColor.copy(alpha = 0.8f),
        baseColor.copy(alpha = 0.5f)
    ),
    start = androidx.compose.ui.geometry.Offset(shimmerX - 500, 0f),
    end = androidx.compose.ui.geometry.Offset(shimmerX + 500, 0f)
)

@Composable
fun ConnectivityIndicator(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isOnline = rememberConnectivityState(context)

    if (!isOnline) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFCC3333),
                            Color(0xFFB22222)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "No Internet Connection",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun rememberConnectivityState(context: Context): Boolean {
    var isOnline by remember { mutableStateOf(true) }

    LaunchedEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline = true
            }

            override fun onLost(network: Network) {
                val activeNetwork = connectivityManager?.activeNetwork
                isOnline = activeNetwork != null
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                isOnline = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }

        connectivityManager?.registerNetworkCallback(
            android.net.NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            networkCallback
        )
    }

    return isOnline
}

@Composable
fun SuccessToast(
    message: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
        delay(2000)
        scale.animateTo(0f, animationSpec = tween(300))
        onDismiss()
    }

    if (scale.value > 0f) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF4CAF50))
                .padding(12.dp)
                .graphicsLayer(scaleX = scale.value, scaleY = scale.value),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

// Extension function for spring animations
fun Modifier.springScale() = this.then(
    Modifier.graphicsLayer {
        scaleX = 1f
        scaleY = 1f
    }
)

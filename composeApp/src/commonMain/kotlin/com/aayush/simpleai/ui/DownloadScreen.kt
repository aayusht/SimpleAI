package com.aayush.simpleai.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aayush.simpleai.ui.theme.AppTheme
import com.aayush.simpleai.ui.theme.AppThemePreviewDarkBackground
import com.aayush.simpleai.ui.theme.backgroundDark
import com.aayush.simpleai.ui.theme.primaryDark
import com.aayush.simpleai.ui.theme.primaryLight
import com.aayush.simpleai.util.AccelerometerData
import com.aayush.simpleai.util.DownloadState
import com.aayush.simpleai.util.createAccelerometerProvider
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import simpleai.composeapp.generated.resources.Res
import simpleai.composeapp.generated.resources.download_progress
import simpleai.composeapp.generated.resources.download_progress_unknown
import simpleai.composeapp.generated.resources.downloading_model
import simpleai.composeapp.generated.resources.error_prefix
import kotlin.compareTo
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

private const val GRID_SIZE = 30
private const val MAX_CELLS = GRID_SIZE * GRID_SIZE

// Simulation constants
private const val DAMPING = 0.9f
private const val GRAVITY_SCALE = 0.15f
private const val JITTER = 0.8f
private const val MAX_VELOCITY = 2f

/**
 * Particle in the fluid simulation.
 */
private class Particle(
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f
)

/**
 * Grid-based fluid simulation state.
 */
private class FluidGrid {
    private val particles = mutableListOf<Particle>()
    private val occupancy = Array(GRID_SIZE) { BooleanArray(GRID_SIZE) }
    
    // Version counter to trigger recomposition
    var version by mutableStateOf(0L)
        private set
    
    fun getFilledCount(): Int = particles.size
    
    fun canAddMore(): Boolean = particles.size < MAX_CELLS
    
    /**
     * Add a new drop at the top-left corner area.
     * Returns true if successfully added.
     */
    fun addDrop(): Boolean {
        if (!canAddMore()) return false
        
        // Try to find an empty cell in the top-left area
        for (y in 0 until GRID_SIZE) {
            // Iterate over x starting from the center, then right, then left, then +2, -2, etc.
            val center = GRID_SIZE / 2
            for (dx in 0 until GRID_SIZE) {
                val x = if (dx % 2 == 0) center + (dx / 2)
                        else center - ((dx + 1) / 2)
                if (x in 0 until GRID_SIZE && !occupancy[x][y]) {
                    val p = Particle(x.toFloat(), y.toFloat())
                    // Add slight random initial velocity
                    p.vx = (Random.nextFloat() - 0.5f) * 0.2f
                    p.vy = (Random.nextFloat() - 0.5f) * 0.2f
                    particles.add(p)
                    occupancy[x][y] = true
                    version++
                    return true
                }
            }
        }
        return false
    }
    
    /**
     * Simulate one step of the fluid based on gravity direction.
     * Gravity is derived from accelerometer data.
     */
    fun simulateStep(accelerometerData: AccelerometerData) {
        val gx = -accelerometerData.x * GRAVITY_SCALE
        val gy = accelerometerData.y * GRAVITY_SCALE
        
        // Shuffle particles to avoid bias in collision resolution
        particles.shuffle()
        
        for (p in particles) {
            val oldIx = p.x.roundToInt().coerceIn(0, GRID_SIZE - 1)
            val oldIy = p.y.roundToInt().coerceIn(0, GRID_SIZE - 1)
            occupancy[oldIx][oldIy] = false
            
            // Apply gravity and jitter
            p.vx += gx + (Random.nextFloat() - 0.5f) * JITTER
            p.vy += gy + (Random.nextFloat() - 0.5f) * JITTER
            
            // Cap velocity
            val speed = sqrt(p.vx * p.vx + p.vy * p.vy)
            if (speed > MAX_VELOCITY) {
                p.vx = (p.vx / speed) * MAX_VELOCITY
                p.vy = (p.vy / speed) * MAX_VELOCITY
            }
            
            var nextX = p.x + p.vx
            var nextY = p.y + p.vy
            
            // Wall collisions with dampening
            if (nextX < 0) {
                nextX = 0f
                p.vx = -p.vx * DAMPING
            } else if (nextX > GRID_SIZE - 1) {
                nextX = (GRID_SIZE - 1).toFloat()
                p.vx = -p.vx * DAMPING
            }
            
            if (nextY < 0) {
                nextY = 0f
                p.vy = -p.vy * DAMPING
            } else if (nextY > GRID_SIZE - 1) {
                nextY = (GRID_SIZE - 1).toFloat()
                p.vy = -p.vy * DAMPING
            }
            
            val nextIx = nextX.roundToInt().coerceIn(0, GRID_SIZE - 1)
            val nextIy = nextY.roundToInt().coerceIn(0, GRID_SIZE - 1)
            
            if (occupancy[nextIx][nextIy]) {
                // Particle collision - bounce back with dampening
                p.vx = -p.vx * DAMPING
                p.vy = -p.vy * DAMPING
                // Stay in current cell
                occupancy[oldIx][oldIy] = true
            } else {
                // Successful move
                p.x = nextX
                p.y = nextY
                occupancy[nextIx][nextIy] = true
            }
        }
        
        version++ // Trigger recomposition
    }
    
    /**
     * Get all filled cell positions for rendering.
     */
    fun getFilledCells(): List<Pair<Int, Int>> {
        // Read version to establish dependency for recomposition
        @Suppress("UNUSED_VARIABLE")
        val currentVersion = version
        
        return particles.map { p ->
            p.x.roundToInt().coerceIn(0, GRID_SIZE - 1) to p.y.roundToInt().coerceIn(0, GRID_SIZE - 1)
        }
    }
}

@Composable
fun DownloadScreen(downloadState: DownloadState) {
    val accelerometerProvider = remember { createAccelerometerProvider() }
    var accelerometerData by remember { mutableStateOf(AccelerometerData()) }
    
    // Start/stop accelerometer
    DisposableEffect(Unit) {
        accelerometerProvider.start()
        onDispose {
            accelerometerProvider.stop()
        }
    }
    
    // Collect accelerometer updates
    LaunchedEffect(Unit) {
        accelerometerProvider.accelerometerData.collect { data ->
            accelerometerData = data
        }
    }

    DownloadScreenWithPhysics(accelerometerData, downloadState)
}

@Composable
private fun DownloadScreenWithPhysics(
    accelerometerData: AccelerometerData,
    downloadState: DownloadState,
) {

    // Track the number of drops that should have been added based on progress
    var lastDropCount by remember { mutableIntStateOf(0) }

    // Grid for the simulation
    val fluidGrid = remember { FluidGrid() }

    // Add new drops based on download progress
    // 1 drop per 1/500th of download = 500 drops at 100%
    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Downloading && downloadState.totalBytes > 0) {
            val targetDropCount = ((downloadState.receivedBytes.toFloat() / downloadState.totalBytes) * MAX_CELLS).toInt()

            while (lastDropCount < targetDropCount && fluidGrid.canAddMore()) {
                fluidGrid.addDrop()
                lastDropCount++
            }
        }
    }

    // Simulation loop
    LaunchedEffect(accelerometerData, downloadState) {
        while (downloadState is DownloadState.Downloading && fluidGrid.canAddMore()) {
            fluidGrid.simulateStep(accelerometerData)
            delay(33) // ~30 FPS for simulation
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Shimmer animation for the title
        val infiniteTransition = rememberInfiniteTransition()
        val shimmerTranslate by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )

        val shimmerColors = listOf(
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.onBackground,
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )

        val brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(shimmerTranslate - 500f, shimmerTranslate),
            end = Offset(shimmerTranslate, shimmerTranslate),
            tileMode = TileMode.Mirror
        )

        // Display-sized title with shimmer
        Text(
            text = stringResource(resource = Res.string.downloading_model),
            style = MaterialTheme.typography.displayMedium.copy(brush = brush),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Fluid simulation container
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            FluidGridCanvas(
                fluidGrid = fluidGrid,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Progress text
        when (downloadState) {
            is DownloadState.Downloading -> {
                Text(
                    text = if (downloadState.totalMB > 0L) {
                        stringResource(
                            resource = Res.string.download_progress,
                            downloadState.receivedMB,
                            downloadState.totalMB,
                        )
                    } else {
                        stringResource(
                            resource = Res.string.download_progress_unknown,
                            downloadState.receivedMB,
                        )
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
            is DownloadState.Error -> {
                Text(
                    text = stringResource(
                        resource = Res.string.error_prefix,
                        downloadState.message,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                Text(
                    text = "Preparing...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun FluidGridCanvas(
    fluidGrid: FluidGrid,
    modifier: Modifier = Modifier
) {
    val filledCells = fluidGrid.getFilledCells()
    
    Canvas(modifier = modifier) {
        val cellWidth = size.width / GRID_SIZE
        val cellHeight = size.height / GRID_SIZE
        
        filledCells.forEach { (x, y) ->
            drawRect(
                color = primaryLight,
                topLeft = Offset(x * cellWidth, y * cellHeight),
                size = Size(cellWidth, cellHeight)
            )
        }
    }
}

@Composable
@Preview(showBackground = true)
fun DownloadScreenPreviewDark() {
    AppThemePreviewDarkBackground {
        var receivedMB by remember { mutableStateOf(value = 0L) }
        LaunchedEffect(key1 = Unit) {
            while (receivedMB < 90) {
                delay(timeMillis = 1000L)
                receivedMB += 1
            }
        }
        DownloadScreenWithPhysics(
            accelerometerData = AccelerometerData(
                x = 0f,
                y = 10f,
                z = 0f
            ),
            downloadState = DownloadState.Downloading(
                receivedBytes = receivedMB * 1024 * 1024,
                totalBytes = 100L * 1024 * 1024,
                bytesPerSecond = 1L * 1024 * 1024,
                remainingMs = (100L - receivedMB) * 1000,
            )
        )
    }
}

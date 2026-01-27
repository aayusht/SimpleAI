package com.aayush.simpleai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aayush.simpleai.ui.theme.AppTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import simpleai.composeapp.generated.resources.Res
import simpleai.composeapp.generated.resources.cancel
import simpleai.composeapp.generated.resources.download_alert_message
import simpleai.composeapp.generated.resources.download_alert_terms
import simpleai.composeapp.generated.resources.proceed
import simpleai.composeapp.generated.resources.download_alert_title
import simpleai.composeapp.generated.resources.not_enough_memory_and_storage
import simpleai.composeapp.generated.resources.welcome_continue
import simpleai.composeapp.generated.resources.welcome_description
import simpleai.composeapp.generated.resources.welcome_title

@Composable
fun WelcomeScreen(
    onProceed: () -> Unit,
    remainingStorage: String,
    cannotDownloadMessage: String?,
) {
    WelcomeScreen(
        onProceed = onProceed,
        remainingStorage = remainingStorage,
        cannotDownloadMessage = cannotDownloadMessage,
        skipAnimation = false,
        showDialogInitially = false,
    )
}

@Composable
private fun WelcomeScreen(
    onProceed: () -> Unit,
    remainingStorage: String,
    cannotDownloadMessage: String?,
    skipAnimation: Boolean,
    showDialogInitially: Boolean,
) {
    val title = stringResource(resource = Res.string.welcome_title)
    val description = stringResource(resource = Res.string.welcome_description).trimIndent()

    var titleCompleted by remember { mutableStateOf(value = false) }
    var descriptionCompleted by remember { mutableStateOf(value = false) }

    var showDialog by remember { mutableStateOf(value = showDialogInitially) }
    val blurRadius by animateDpAsState(targetValue = if (showDialog) 8.dp else 0.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .blur(radius = blurRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(all = 24.dp)
                .verticalScroll(state = rememberScrollState()),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {

            Spacer(modifier = Modifier.weight(weight = 1f))

            FakeAiOutputWithSpacer(
                skipAnimation = skipAnimation,
                text = title,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                startOutput = true,
                onCompleted = { titleCompleted = true },
                modifier = Modifier.padding(bottom = 16.dp)
            )

            FakeAiOutputWithSpacer(
                skipAnimation = skipAnimation,
                text = description,
                style = MaterialTheme.typography.titleLarge,
                startOutput = titleCompleted,
                onCompleted = {
                    delay(timeMillis = 200)
                    descriptionCompleted = true
                },
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Spacer(modifier = Modifier.weight(weight = 3f))
        }

        AnimatedVisibility(
            visible = skipAnimation || (titleCompleted && descriptionCompleted),
            enter = fadeIn(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(all = 16.dp)
        ) {
            Button(
                onClick = { showDialog = true },
                shape = RoundedCornerShape(size = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Text(
                    modifier = Modifier.padding(vertical = 8.dp),
                    text = stringResource(resource = Res.string.welcome_continue),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            title = { Text(text = stringResource(resource = Res.string.download_alert_title)) },
            text = {
                val text = cannotDownloadMessage ?: stringResource(
                    resource = Res.string.download_alert_message,
                    remainingStorage,
                )
                Column {
                    Text(text = text)
                    if (cannotDownloadMessage == null) {
                        Spacer(modifier = Modifier.height(height = 16.dp))
                        Text(
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            text = stringResource(
                                resource = Res.string.download_alert_terms,
                            ).trimIndent()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = cannotDownloadMessage == null,
                    onClick = {
                        showDialog = false
                        onProceed()
                    }
                ) {
                    Text(text = stringResource(resource = Res.string.proceed))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(text = stringResource(resource = Res.string.cancel))
                }
            }
        )
    }
}

@Composable
fun FakeAiOutputWithSpacer(
    modifier: Modifier = Modifier,
    startOutput: Boolean,
    skipAnimation: Boolean,
    text: String,
    style: TextStyle,
    fontWeight: FontWeight = FontWeight.Normal,
    onCompleted: suspend () -> Unit,
) {
    val animatedText by fakeAiOutput(text = text, onCompleted = onCompleted)
    Box(modifier = Modifier.wrapContentSize()) {
        if (startOutput || skipAnimation) {
            Text(
                text = if (skipAnimation) text else animatedText,
                style = style,
                fontWeight = fontWeight,
                modifier = modifier,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        // for spacing
        Text(
            text = text,
            style = style,
            modifier = modifier.alpha(alpha = 0f),
        )
    }
}

@Composable
@Preview(showBackground = true)
fun WelcomeScreenPreview() {
    AppTheme {
        WelcomeScreen(
            onProceed = {},
            remainingStorage = "10.0",
            cannotDownloadMessage = null,
            skipAnimation = true,
            showDialogInitially = true,
        )
    }
}

@Composable
@Preview(showBackground = true)
fun WelcomeScreenPreviewNotEnoughStorage() {
    AppTheme {
        WelcomeScreen(
            onProceed = {},
            remainingStorage = "10.0",
            cannotDownloadMessage = stringResource(
                resource = Res.string.not_enough_memory_and_storage,
                "10.0",
                "20.0",
                "10.0",
                "20.0",
            ),
            skipAnimation = true,
            showDialogInitially = true,
        )
    }
}

@Composable
@Preview(showBackground = true)
fun WelcomeScreenPreviewAnimated() {
    AppTheme {
        WelcomeScreen(
            onProceed = {},
            remainingStorage = "10.0",
            cannotDownloadMessage = null,
            skipAnimation = false,
            showDialogInitially = true,
        )
    }
}


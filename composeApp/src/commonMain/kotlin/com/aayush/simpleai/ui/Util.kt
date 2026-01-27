package com.aayush.simpleai.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

@Composable
internal fun fakeAiOutput(
    text: String,
    initialDelayMillis: Long = 500,
    perTokenDelayMillis: Long = 100,
    onCompleted: suspend () -> Unit = {}
): MutableState<String> {
    val targetContentWords = text.split(' ').iterator()
    val content = remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        delay(timeMillis = initialDelayMillis)
        while(targetContentWords.hasNext()) {
            content.value += targetContentWords.next()
            if (targetContentWords.hasNext()) {
                content.value += " "
            }
            delay(timeMillis = perTokenDelayMillis)
        }
        onCompleted()
    }
    return content
}

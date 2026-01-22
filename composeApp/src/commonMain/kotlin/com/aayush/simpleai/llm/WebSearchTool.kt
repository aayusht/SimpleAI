package com.aayush.simpleai.llm

import co.touchlab.kermit.Logger
import com.aayush.simpleai.util.*
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlHandler
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlParser
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.time.Clock


private const val USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_3_2 like Mac OS X) " +
        "AppleWebKit/605.1.15 (KHTML, like Gecko) GSA/360.1.737798518 Mobile/15E148 Safari/604."

private const val DDG_LITE_URL = "https://duckduckgo.com/lite/?q="

private suspend fun HttpClient.getHtml(url: String): String = get(url) {
    header("User-Agent", USER_AGENT)
}.bodyAsText()

internal suspend fun webSearch(query: String, engine: EnginePtr): String {
    try {
        val client = HttpClient()

        val searchHtml = client.getHtml("$DDG_LITE_URL${query.encodeURLParameter()}")

        val links = parseDuckDuckGoLiteLinks(searchHtml, limit = 10)
        if (links.isEmpty()) return "No results found for '$query'."

        val combinedText = coroutineScope {
            val channel = Channel<String?>(links.size)
            links.forEach { (title, url) ->
                launch {
                    try {
                        Logger.i("Extracting link: $title at ${Clock.System.now().epochSeconds}")
                        val siteHtml = client.getHtml(url)
                        val mainText = extractMainText(siteHtml)
                        channel.send("Source: $title\nContent: $mainText\n\n")
                    } catch (e: Exception) {
                        Logger.e("Failed to extract $url: ${e.message}")
                        channel.send(null)
                    }
                }
            }

            val successResults = mutableListOf<String>()
            var processed = 0
            while (processed < links.size && successResults.size < 5) {
                channel.receive()?.let(successResults::add)
                processed++
            }
            coroutineContext.cancelChildren()
            successResults
                .sortedBy { it.length }
                .filter { it.length > 100 }
                .take(3)
                .joinToString("") { it.take(1000) }
        }
        client.close()

        if (combinedText.isEmpty()) return "Could not extract content from search results."
        Logger.i("Now it is ${Clock.System.now().epochSeconds}")

        return combinedText
    } catch (e: Exception) {
        throw e
    }
}

/**
 * Extracts links from DuckDuckGo Lite HTML, stopping after [limit] links.
 */
internal fun parseDuckDuckGoLiteLinks(html: String, limit: Int): List<Pair<String, String>> {

    Logger.i("Starting parsing at ${Clock.System.now().epochSeconds}")

    val handler = DuckDuckGoResultsHandler(limit)
    val parser = KsoupHtmlParser(handler)
    parser.write(html)
    parser.end()
    Logger.i("Finished parsing at ${Clock.System.now().epochSeconds}")

    return handler.results
}

/**
 * Simple main text extraction.
 */
internal fun extractMainText(html: String): String {
    val content = mutableListOf<String>()

    val handler = object : KsoupHtmlHandler {

        var currentText = StringBuilder()
        var inContentTag = false
        var skipContent = false

        override fun onOpenTag(name: String, attributes: Map<String, String>, isImplied: Boolean) {
            when (name) {
                "p", "h1", "h2", "h3", "article" -> inContentTag = true
                "td", "div" -> {
                    if (attributes["class"]?.contains("snippet") == true || 
                        attributes["class"]?.contains("content") == true) {
                        inContentTag = true
                    }
                }
                "script", "style", "nav", "footer" -> skipContent = true
            }
        }

        override fun onText(text: String) {
            if (skipContent) return
            if (inContentTag) {
                currentText.append(text)
            }
        }

        override fun onCloseTag(name: String, isImplied: Boolean) {
            when (name) {
                "p", "h1", "h2", "h3", "article", "td", "div" -> {
                    if (inContentTag) {
                        val text = currentText.toString().trim()
                        if (text.length > 20) {
                            content.add(text)
                        }
                        currentText = StringBuilder()
                        inContentTag = false
                    }
                }
                "script", "style", "nav", "footer" -> skipContent = false
            }
        }
    }

    val parser = KsoupHtmlParser(handler)
    parser.write(html)
    parser.end()

    return if (content.isNotEmpty()) {
        content.joinToString(" ")
    } else {
        // Fallback: extract all text while skipping scripts/styles
        val allText = StringBuilder()
        val fallbackHandler = object : KsoupHtmlHandler {
            var skip = false
            override fun onOpenTag(name: String, attributes: Map<String, String>, isImplied: Boolean) {
                if (name == "script" || name == "style" || name == "nav" || name == "footer") skip = true
            }
            override fun onText(text: String) {
                if (!skip) allText.append(text).append(" ")
            }
            override fun onCloseTag(name: String, isImplied: Boolean) {
                if (name == "script" || name == "style" || name == "nav" || name == "footer") skip = false
            }
        }
        KsoupHtmlParser(fallbackHandler).apply {
            write(html)
            end()
        }
        allText.toString().replace("""\s+""".toRegex(), " ").trim()
    }
}

private class DuckDuckGoResultsHandler(val limit: Int) : KsoupHtmlHandler {

    private val internalResults = mutableListOf<Pair<String, String>>()
    private var currentTitle = StringBuilder()
    private var currentUrl: String? = null
    private var inResultLink = false

    val results: List<Pair<String, String>>
        get() = internalResults

    override fun onOpenTag(name: String, attributes: Map<String, String>, isImplied: Boolean) {
        if (internalResults.size >= limit) return
        if (name == "a" && (attributes["class"] == "result-link" || attributes["class"] == "'result-link'")) {
            inResultLink = true
            val rawUrl = attributes["href"]
            if (rawUrl != null) {
                currentUrl = if (rawUrl.contains("uddg=")) {
                    val uddg = rawUrl.substringAfter("uddg=").substringBefore("&")
                    uddg.decodeURLPart()
                } else if (rawUrl.startsWith("//")) {
                    "https:$rawUrl"
                } else {
                    rawUrl
                }
            }
        }
    }

    override fun onText(text: String) {
        if (inResultLink) {
            currentTitle.append(text)
        }
    }

    override fun onCloseTag(name: String, isImplied: Boolean) {
        if (name == "a" && inResultLink) {
            val url = currentUrl
            val title = currentTitle.toString().trim()
            if (internalResults.size < limit && url != null && !url.contains("duckduckgo.com") && title.isNotBlank()) {
                internalResults.add(title to url)
            }
            currentTitle = StringBuilder()
            currentUrl = null
            inResultLink = false
        }
    }
}

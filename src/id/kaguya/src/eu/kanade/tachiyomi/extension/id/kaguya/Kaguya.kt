package eu.kanade.tachiyomi.extension.id.kaguya

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Kaguya :
    Madara(
        "Kaguya",
        "https://01.kaguya.pro",
        "id",
        dateFormat = SimpleDateFormat("d MMMM", Locale("en")),
    ) {

    override val client: OkHttpClient = super.client.newBuilder()
        .readTimeout(1, TimeUnit.MINUTES)
        .build()

    private val json: Json by injectLazy()

    override val id = 1557304490417397104

    override val mangaSubString = "series"

    override val mangaDetailsSelectorTitle = "h1.post-title"
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div"
    override val mangaDetailsSelectorThumbnail = "head meta[property='og:image']" // Same as browse

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/popular/${searchPage(page)}", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/all-series/${searchPage(page)}", headers)

    override fun genresRequest() = GET(
        "$baseUrl/api/genres",
        headersBuilder()
            .set("Accept", "application/json, text/plain, */*")
            .set("X-App-Secret", "dfdf72051dbfdc7d76889ebd31324e74")
            .build(),
    )

    override fun imageFromElement(element: Element): String? {
        if (element.hasAttr("data-aesir")) {
            val decoded = Base64.decode(element.attr("data-aesir"), Base64.DEFAULT).toString(Charsets.UTF_8).trim()
            if (decoded.isNotEmpty()) return decoded
        }

        return super.imageFromElement(element)
            ?.takeIf { it.isNotEmpty() }
            ?: element.attr("content") // Thumbnail from <head>
    }

    // ============================== Chapters ==============================
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable { fetchAllChapters(manga) }

    private fun fetchAllChapters(manga: SManga): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var page = 1
        while (true) {
            val response = client.newCall(POST("${getMangaUrl(manga)}ajax/chapters?t=${page++}", xhrHeaders)).execute()
            val document = response.asJsoup()
            val currentPage = document.select(chapterListSelector())
                .map(::chapterFromElement)

            chapters += currentPage
            response.close()

            if (currentPage.isEmpty()) {
                return chapters
            }
        }
    }

    // ============================== Filter ==============================
    override fun parseGenres(document: Document): List<Genre> {
        val element = runCatching { json.parseToJsonElement(document.text()) }.getOrNull() ?: return emptyList()

        val candidates = buildList<JsonElement> {
            add(element)

            if (element is JsonObject) {
                listOf("data", "genres", "result", "items").forEach { key ->
                    element[key]?.let(::add)
                }
            }
        }

        return candidates.asSequence()
            .flatMap { candidate ->
                when (candidate) {
                    is JsonArray -> candidate.asSequence()
                    is JsonObject -> sequenceOf(candidate)
                    else -> emptySequence()
                }
            }
            .mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull
                    ?: obj["title"]?.jsonPrimitive?.contentOrNull
                    ?: obj["label"]?.jsonPrimitive?.contentOrNull
                    ?: obj["genre"]?.jsonPrimitive?.contentOrNull
                    ?: obj["text"]?.jsonPrimitive?.contentOrNull
                    ?: obj["value"]?.jsonPrimitive?.contentOrNull
                val id = obj["slug"]?.jsonPrimitive?.contentOrNull
                    ?: obj["id"]?.jsonPrimitive?.contentOrNull
                    ?: obj["code"]?.jsonPrimitive?.contentOrNull
                    ?: obj["key"]?.jsonPrimitive?.contentOrNull
                    ?: name

                if (name.isNullOrBlank() || id.isNullOrBlank()) null else Genre(name, id)
            }
            .distinctBy { it.id }
            .toList()
    }

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}

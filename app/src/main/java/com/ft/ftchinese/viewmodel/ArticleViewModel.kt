package com.ft.ftchinese.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ft.ftchinese.R
import com.ft.ftchinese.database.StarredArticle
import com.ft.ftchinese.model.content.*
import com.ft.ftchinese.ui.base.ShareItem
import com.ft.ftchinese.util.Fetch
import com.ft.ftchinese.store.FileCache
import com.ft.ftchinese.util.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info

class ArticleViewModel(
        private val cache: FileCache,
        private val followingManager: FollowingManager
) : ViewModel(), AnkoLogger {

    val inProgress = MutableLiveData<Boolean>()

    // Notify ArticleActivity whether to display language
    // switch button or not.
    val bilingual = MutableLiveData<Boolean>()

    val currentLang = MutableLiveData<Language>()

    // Notify ArticleActivity the meta data for starring.
    val articleLoaded = MutableLiveData<StarredArticle>()

    // Notify StoryFragment whether cached is found
    val cacheFound: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>()
    }

    // Notify StoryFragment that html is ready to be loaded
    // into WebView.
    val renderResult: MutableLiveData<Result<String>> by lazy {
        MutableLiveData<Result<String>>()
    }

    val shareItem = MutableLiveData<ShareItem>()

    private var template: String? = null

    // Tell host activity that content is loaded.
    // Host could then log view event.
    fun webLoaded(data: StarredArticle) {
        articleLoaded.value = data
    }

    // Host activity tells fragment to switch content.
    fun switchLang(lang: Language) {
        currentLang.value = lang
    }

    fun loadFromCache(item: Teaser, lang: Language) {
        val cacheName = item.cacheNameJson()
        if (cacheName.isBlank()) {
            cacheFound.value = false
            return
        }

        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    cache.loadText(cacheName)
                }

                if (data.isNullOrBlank()) {
                    cacheFound.value = false
                    return@launch
                }

                val story = json.parse<Story>(data)

                if (story == null) {
                    cacheFound.value = false
                    return@launch
                }

                val html = render(
                        item = item,
                        story = story,
                        lang = lang,
                        follows = followingManager.loadForJS())

                if (html == null) {
                    cacheFound.value = false
                    return@launch
                }

                // The HTML string to be loaded.
                renderResult.value = Result.Success(html)

                // Only set update articleLoaded for initial loading.
                articleLoaded.value = story.toStarredArticle(item)

                // Notify whether this is bilingual content
                bilingual.value = story.isBilingual

            } catch (e: Exception) {
                info(e)
                cacheFound.value = false
            }
        }
    }

    fun loadFromRemote(item: Teaser, lang: Language) {
        val url = item.contentUrl()
        info("Loading json data from $url")

        if (url.isBlank()) {
            renderResult.value = Result.LocalizedError(R.string.api_empty_url)
            return
        }

        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    Fetch().get(url).responseString()
                }

                if (data.isNullOrBlank()) {
                    renderResult.value = Result.LocalizedError(R.string.api_server_error)
                    return@launch
                }

                // Cache the downloaded data.
                launch(Dispatchers.IO) {
                    cache.saveText(item.cacheNameJson(), data)
                }

                val story = json.parse<Story>(data)

                if (story == null) {
                    renderResult.value = Result.LocalizedError(R.string.api_server_error)
                    return@launch
                }

                val html = render(
                        item = item,
                        story = story,
                        lang = lang,
                        follows = followingManager.loadForJS()
                )

                if (html == null) {
                    renderResult.value = Result.LocalizedError(R.string.loading_failed)
                    return@launch
                }

                renderResult.value = Result.Success(html)

                // Only update it for initial loading.
                articleLoaded.value = story.toStarredArticle(item)
                bilingual.value = story.isBilingual

            } catch (e: Exception) {
                info(e)
                renderResult.value = parseException(e)
            }
        }
    }

    private suspend fun render(item: Teaser, story: Story, lang: Language, follows: JSFollows) = withContext(Dispatchers.Default) {
        if (template == null) {
            template = cache.readStoryTemplate()
        }

        item.renderStory(
                template = template,
                story = story,
                language = lang,
                follows = follows
        )
    }

    fun share(item: ShareItem) {
        shareItem.value = item
    }
}

package com.mohdshayan.inkhorn.content

import android.content.Context
import com.mohdshayan.inkhorn.engine.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Reads the bundled story once. Nothing is ever fetched; the whole game ships in assets/story.json. */
class ContentLoader(private val context: Context) {
    private val mutex = Mutex()
    @Volatile private var cached: Content? = null

    suspend fun load(): Content = cached ?: mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            val text = context.assets.open(STORY).bufferedReader().use { it.readText() }
            Content.parse(text)
        }.also { cached = it }
    }

    suspend fun asset(name: String): String = withContext(Dispatchers.IO) {
        context.assets.open(name).bufferedReader().use { it.readText() }
    }

    companion object {
        const val STORY = "story.json"
    }
}

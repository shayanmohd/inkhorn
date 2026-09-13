package com.mohdshayan.inkhorn.di

import android.content.Context
import com.mohdshayan.inkhorn.content.ContentLoader
import com.mohdshayan.inkhorn.data.GameRepository
import com.mohdshayan.inkhorn.data.SaveTransfer
import com.mohdshayan.inkhorn.data.db.AppDatabase
import com.mohdshayan.inkhorn.data.prefs.AppPrefs

/**
 * Manual dependency container. Initialised in App.onCreate, and idempotent so the chapter-note
 * worker may call init() again with whatever context it has.
 */
object ServiceLocator {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                if (appContext == null) appContext = context.applicationContext
            }
        }
    }

    private fun ctx(): Context =
        appContext ?: error("ServiceLocator.init() must be called before use")

    val appPrefs: AppPrefs by lazy { AppPrefs(ctx()) }
    val database: AppDatabase by lazy { AppDatabase.get(ctx()) }
    val gameDao get() = database.gameDao()
    val contentLoader: ContentLoader by lazy { ContentLoader(ctx()) }
    val repository: GameRepository by lazy { GameRepository(ctx(), gameDao, appPrefs, contentLoader) }
    val saveTransfer: SaveTransfer by lazy { SaveTransfer(ctx()) }
}

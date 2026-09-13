package com.mohdshayan.inkhorn.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mohdshayan.inkhorn.di.ServiceLocator
import com.mohdshayan.inkhorn.engine.CatchUp
import com.mohdshayan.inkhorn.engine.CatchUpStop
import com.mohdshayan.inkhorn.engine.Engine
import com.mohdshayan.inkhorn.engine.SaveCheck
import com.mohdshayan.inkhorn.engine.SaveCodec
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/**
 * Runs once, when the projection said the chapter goal would be met. Replays the saved snapshot
 * to now and posts the note only if the goal really was met.
 */
class ChapterNoteWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ServiceLocator.init(applicationContext)
        if (!ServiceLocator.appPrefs.current().chapterNotes) return Result.success()
        val dao = ServiceLocator.gameDao
        val run = dao.openRun() ?: return Result.success()
        val snap = dao.snapshot(run.id) ?: return Result.success()
        val content = ServiceLocator.contentLoader.load()
        val state = SaveCodec.decodeState(snap.stateJson)?.takeIf { SaveCheck.playable(content, it) } ?: return Result.success()
        val result = CatchUp.run(Engine(content), state, CatchUp.elapsedSeconds(state.lastWallClockMs, System.currentTimeMillis()))
        if (result.stop == CatchUpStop.GOAL) Notes.post(applicationContext, content.chapter(state.chapter).noteLine)
        return Result.success()
    }

    companion object {
        const val NAME = "chapter-note"

        fun enqueue(context: Context, seconds: Double) {
            val request = OneTimeWorkRequestBuilder<ChapterNoteWorker>()
                .setInitialDelay(ceil(seconds).toLong() + 5, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}

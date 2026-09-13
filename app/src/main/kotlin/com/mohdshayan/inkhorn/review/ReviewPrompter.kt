package com.mohdshayan.inkhorn.review

import android.app.Activity
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory

/** Asks for a Play review once, on returning to the ledger after chapter three. Never on first launch. */
object ReviewPrompter {
    suspend fun prompt(activity: Activity) {
        try {
            val manager = ReviewManagerFactory.create(activity)
            val info = manager.requestReview()
            manager.launchReview(activity, info)
        } catch (_: Exception) {
            // Without the Play Store there is nothing to show; the game carries on.
        }
    }
}

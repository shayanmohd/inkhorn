package com.mohdshayan.inkhorn

import android.app.Application
import com.mohdshayan.inkhorn.di.ServiceLocator
import com.mohdshayan.inkhorn.work.Notes

/** Wires the service locator and the notification channel, then starts loading the save. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        Notes.createChannel(this)
        ServiceLocator.repository.start()
    }
}

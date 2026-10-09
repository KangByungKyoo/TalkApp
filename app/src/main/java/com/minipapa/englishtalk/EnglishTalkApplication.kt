package com.minipapa.englishtalk

import android.app.Application
import com.google.firebase.FirebaseApp
import com.minipapa.englishtalk.data.AppCheckInstaller

class EnglishTalkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.initializeApp(this) != null) AppCheckInstaller.install()
    }
}

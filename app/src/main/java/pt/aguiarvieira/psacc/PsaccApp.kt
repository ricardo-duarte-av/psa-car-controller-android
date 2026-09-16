package pt.aguiarvieira.psacc

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import pt.aguiarvieira.psacc.notifications.NotificationScheduler
import pt.aguiarvieira.psacc.notifications.VehicleNotifier
import javax.inject.Inject

@HiltAndroidApp
class PsaccApp : Application() {

    @Inject lateinit var notifier: VehicleNotifier
    @Inject lateinit var notificationScheduler: NotificationScheduler

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        notificationScheduler.start()
    }
}

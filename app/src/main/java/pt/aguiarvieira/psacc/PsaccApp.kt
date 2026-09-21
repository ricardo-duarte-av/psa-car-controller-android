package pt.aguiarvieira.psacc

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import pt.aguiarvieira.psacc.data.auth.ConnectionRepository
import pt.aguiarvieira.psacc.data.network.psaccImageLoader
import pt.aguiarvieira.psacc.notifications.NotificationScheduler
import pt.aguiarvieira.psacc.notifications.VehicleNotifier
import javax.inject.Inject

@HiltAndroidApp
class PsaccApp : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var notifier: VehicleNotifier
    @Inject lateinit var notificationScheduler: NotificationScheduler
    @Inject lateinit var connection: ConnectionRepository

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        notificationScheduler.start()
    }

    /** Coil's app-wide loader, carrying the daemon's basic-auth so car pictures load. */
    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader =
        psaccImageLoader(this, connection)
}

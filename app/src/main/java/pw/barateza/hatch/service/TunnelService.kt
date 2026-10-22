package pw.barateza.hatch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import pw.barateza.hatch.MainActivity
import pw.barateza.hatch.R
import pw.barateza.hatch.runtime.HatchRuntime

/**
 * Keeps the process (and therefore the SSH tunnel) alive in the background.
 *
 * - Visible, ongoing foreground notification with a Disconnect action.
 * - `START_STICKY` + persisted "desired" flag: if Android kills the process, the service is
 *   recreated and asks the controller to bring the engine back (using the cached Access token
 *   and stored SSH key, so it usually reconnects without user interaction).
 * - A partial wake lock keeps the CPU responsive for tunnel traffic while the screen is off.
 * - `specialUse` foreground type avoids Android 15's six-hour dataSync FGS timeout.
 *
 * The engine itself lives in [HatchRuntime]; this service owns the notification and lifecycle.
 */
class TunnelService : Service() {
  private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }
  private var wakeLock: PowerManager.WakeLock? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    val manager = getSystemService(NotificationManager::class.java)
    // v2: the original low-importance channel landed under "Silent", which made it easy to miss.
    // Default importance + no sound still posts quietly but in the main notification section.
    manager.deleteNotificationChannel("hatch.tunnel")
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "Tunnel", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "Ongoing Hatch tunnel; includes a Disconnect action"
        setShowBadge(false)
        setSound(null, null)
        enableVibration(false)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
      },
    )
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      prefs.edit().putBoolean(KEY_DESIRED, false).apply()
      HatchRuntime.peek()?.stopTunnelFromService()
      releaseWakeLock()
      ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
      stopSelf()
      return START_NOT_STICKY
    }

    if (intent?.action == ACTION_START) {
      prefs.edit().putBoolean(KEY_DESIRED, true).apply()
      intent.getStringExtra(EXTRA_LABEL)?.let { prefs.edit().putString(KEY_LABEL, it).apply() }
    }

    val label = prefs.getString(KEY_LABEL, "127.0.0.1:3080") ?: "127.0.0.1:3080"
    startForegroundCompat(label)
    acquireWakeLock()

    // A null intent means Android recreated a sticky service after killing the process.
    // Bring the real engine back; the cached Access token + SSH key make this automatic.
    if (intent == null && prefs.getBoolean(KEY_DESIRED, false)) {
      HatchRuntime.peek()?.ensureTunnelRunning()
    }
    return START_STICKY
  }

  private fun startForegroundCompat(label: String) {
    val type =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
      } else {
        0
      }
    ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(label), type)
  }

  private fun buildNotification(label: String): Notification {
    val open =
      PendingIntent.getActivity(
        this,
        1,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    val stop =
      PendingIntent.getService(
        this,
        2,
        Intent(this, TunnelService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_hatch)
      .setContentTitle("Hatch · tunnel running")
      .setContentText("Local harness at 127.0.0.1:3080")
      .setSubText(label)
      .setContentIntent(open)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setShowWhen(false)
      .setSilent(true)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .addAction(0, "Disconnect", stop)
      .build()
  }

  private fun acquireWakeLock() {
    if (wakeLock?.isHeld == true) return
    val power = getSystemService(PowerManager::class.java) ?: return
    wakeLock =
      power
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Hatch:tunnel")
        .apply {
          setReferenceCounted(false)
          acquire()
        }
  }

  private fun releaseWakeLock() {
    runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
    wakeLock = null
  }

  override fun onDestroy() {
    releaseWakeLock()
    super.onDestroy()
  }

  companion object {
    const val ACTION_START = "pw.barateza.hatch.START"
    const val ACTION_STOP = "pw.barateza.hatch.STOP"
    const val EXTRA_LABEL = "label"
    private const val CHANNEL_ID = "hatch.tunnel.v2"
    private const val NOTIFICATION_ID = 41
    private const val PREFS_NAME = "hatch.service"
    private const val KEY_DESIRED = "desired"
    private const val KEY_LABEL = "label"

    /** Called when the user explicitly disconnects so a sticky restart won't reconnect. */
    fun markStopped(context: Context) {
      context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_DESIRED, false).apply()
    }
  }
}

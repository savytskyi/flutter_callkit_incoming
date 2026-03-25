package com.hiennv.flutter_callkit_incoming
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.content.ContextCompat

class CallkitNotificationService : Service() {
    
    companion object {
        
        private val ActionForeground = listOf(
            CallkitConstants.ACTION_CALL_START,
            CallkitConstants.ACTION_CALL_ACCEPT
        )
        
        fun startServiceWithAction(context: Context, action: String, data: Bundle?) {
            val intent = Intent(context, CallkitNotificationService::class.java).apply {
                this.action = action
                putExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && intent.action in ActionForeground) {
                data?.let {
                    if (it.getBoolean(CallkitConstants.EXTRA_CALLKIT_CALLING_SHOW, true)) {
                        ContextCompat.startForegroundService(context, intent)
                    } else {
                        context.startService(intent)
                    }
                }
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, CallkitNotificationService::class.java)
            context.stopService(intent)
        }
        
    }
    
    private var localSoundPlayerManager: CallkitSoundPlayerManager? = null
    private var localNotificationManager: CallkitNotificationManager? = null
    
    private fun getCallkitNotificationManager(): CallkitNotificationManager {
        
        FlutterCallkitIncomingPlugin.getInstance()
            ?.getCallkitNotificationManager()
            ?.let { return it }
        localNotificationManager?.let { return it }
        val soundManager = localSoundPlayerManager
            ?: CallkitSoundPlayerManager(applicationContext).also {
                localSoundPlayerManager = it
            }
        return CallkitNotificationManager(applicationContext, soundManager).also {
            localNotificationManager = it
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        
        val data = intent?.getBundleExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA)
            ?: run {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        when (intent.action) {
            CallkitConstants.ACTION_CALL_START -> {
                if (data.getBoolean(CallkitConstants.EXTRA_CALLKIT_CALLING_SHOW, true)) {
                    getCallkitNotificationManager().createNotificationChanel(data)
                    showOngoingCallNotification(data, startId)
                } else {
                    stopSelf(startId)
                }
            }
            CallkitConstants.ACTION_CALL_ACCEPT -> {
                getCallkitNotificationManager().clearIncomingNotification(data, true)
                if (data.getBoolean(CallkitConstants.EXTRA_CALLKIT_CALLING_SHOW, true)) {
                    showOngoingCallNotification(data, startId)
                } else {
                    stopSelf(startId)
                }
            }
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }
    
    @SuppressLint("MissingPermission")
    private fun showOngoingCallNotification(bundle: Bundle, startId: Int) {
        val callkitNotification =
            getCallkitNotificationManager().getOnGoingCallNotification(bundle, false)
                ?: run {
                    stopSelf(startId)
                    return
                }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                callkitNotification.id,
                callkitNotification.notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        } else {
            startForeground(callkitNotification.id, callkitNotification.notification)
        }
    }
    
    override fun onDestroy() {
        localNotificationManager?.destroy()
        localNotificationManager = null
        localSoundPlayerManager = null
        super.onDestroy()
    }
    
    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        stopSelf()
    }
}

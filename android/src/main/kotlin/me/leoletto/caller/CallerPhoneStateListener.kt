package me.leoletto.caller

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor.DartCallback
import io.flutter.embedding.engine.loader.FlutterLoader
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.FlutterCallbackInformation
import java.time.Duration
import java.time.ZonedDateTime
import java.util.ArrayList

class CallerPhoneStateListener internal constructor(private val context: Context, private val flutterLoader: FlutterLoader) : PhoneStateListener() {
    private var previousState = 0
    private var sBackgroundFlutterEngine: FlutterEngine? = null
    private var channel: MethodChannel? = null
    private var callbackHandler: Long? = null
    private var callbackHandlerUser: Long? = null
    private var time: ZonedDateTime? = null

    @RequiresApi(Build.VERSION_CODES.O)
    @Synchronized
    override fun onCallStateChanged(state: Int, incomingNumber: String) {
        // Prepare the arguments to be sent to the callback
        val arguments = ArrayList<Any?>()
        if (sBackgroundFlutterEngine == null) {
            callbackHandler = context.getSharedPreferences(
                    CallerPlugin.PLUGIN_NAME,
                    Context.MODE_PRIVATE
            ).getLong(CallerPlugin.CALLBACK_SHAREDPREFERENCES_KEY, 0)
            callbackHandlerUser = context.getSharedPreferences(
                    CallerPlugin.PLUGIN_NAME,
                    Context.MODE_PRIVATE
            ).getLong(CallerPlugin.CALLBACK_USER_SHAREDPREFERENCES_KEY, 0)
            if (callbackHandler == 0L || callbackHandlerUser == 0L) {
                Log.e(CallerPlugin.PLUGIN_NAME, "Fatal: No callback registered")
                return
            }
            Log.d(CallerPlugin.PLUGIN_NAME, "Found callback handler $callbackHandler")
            Log.d(CallerPlugin.PLUGIN_NAME, "Found user callback handler $callbackHandlerUser")

            // Retrieve the actual callback information needed to invoke it.
            val callbackInfo = FlutterCallbackInformation.lookupCallbackInformation(callbackHandler!!)
            if (callbackInfo == null) {
                Log.e(CallerPlugin.PLUGIN_NAME, "Fatal: failed to find callback")
                return
            }
            sBackgroundFlutterEngine = FlutterEngine(context)
            val args = DartCallback(
                    context.assets,
                    flutterLoader.findAppBundlePath(),
                    callbackInfo
            )

            // Start running callback dispatcher code in our background FlutterEngine instance.
            sBackgroundFlutterEngine!!.dartExecutor.executeDartCallback(args)
        }
        arguments.add(callbackHandler)
        arguments.add(callbackHandlerUser)
        arguments.add(incomingNumber)

        // Create the MethodChannel used to communicate between the callback
        // dispatcher and this GeofencingService instance.
        channel = MethodChannel(
                sBackgroundFlutterEngine!!.dartExecutor.binaryMessenger,
                CallerPlugin.PLUGIN_NAME + "_background"
        )
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> {
                if (previousState == TelephonyManager.CALL_STATE_OFFHOOK) {
                    val now = ZonedDateTime.now()
                    val duration = Duration.between(time, now)
                    Log.d(CallerPlugin.PLUGIN_NAME, "Phone State event CALL_ENDED with number $incomingNumber")
                    arguments.add("callEnded")
                    arguments.add(duration.toMillis() / 1000)
                    time = null
                    channel!!.invokeMethod("incomingCallEnded", arguments)
                } else if (previousState == TelephonyManager.CALL_STATE_RINGING) {
                    Log.d(CallerPlugin.PLUGIN_NAME, "Phone State event INCOMING_CALL_MISSED $incomingNumber")
                    arguments.add("onMissedCall")
                    arguments.add(null)
                    channel!!.invokeMethod("onMissedCall", arguments)
                }
                previousState = TelephonyManager.CALL_STATE_IDLE
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (previousState == TelephonyManager.CALL_STATE_RINGING) {
                    Log.d(CallerPlugin.PLUGIN_NAME, "Phone State event INCOMING_CALL_ANSWERED $incomingNumber")
                    arguments.add("onIncomingCallAnswered")
                    arguments.add(null)
                    channel!!.invokeMethod("onIncomingCallAnswered", arguments)
                }
                previousState = TelephonyManager.CALL_STATE_OFFHOOK

                // Get the current time to count later the duration of the call
                time = ZonedDateTime.now()
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                if (previousState == TelephonyManager.CALL_STATE_IDLE) {
                    Log.d(CallerPlugin.PLUGIN_NAME, "Phone State event INCOMING_CALL_RECEIVED $incomingNumber")
                    arguments.add("onIncomingCallReceived")
                    arguments.add(null)
                    channel!!.invokeMethod("onIncomingCallReceived", arguments)
                }
                previousState = TelephonyManager.CALL_STATE_RINGING
            }
        }
    }
}
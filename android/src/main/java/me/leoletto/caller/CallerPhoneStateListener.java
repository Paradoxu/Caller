package me.leoletto.caller;

import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;

import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.loader.FlutterLoader;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.view.FlutterCallbackInformation;

public class CallerPhoneStateListener extends PhoneStateListener {
    private int previousState;
    private Context context;
    private FlutterLoader flutterLoader;
    private FlutterEngine sBackgroundFlutterEngine;
    private MethodChannel channel;
    private Long callbackHandler = null;
    private Long callbackHandlerUser = null;
    private ZonedDateTime time;

    CallerPhoneStateListener(Context context, FlutterLoader flutterLoader){
        this.context = context;
        this.flutterLoader = flutterLoader;
    }

    public synchronized void onCallStateChanged(int state, String incomingNumber) {
        // Prepare the arguments to be sent to the callback
        ArrayList<Object> arguments = new ArrayList<>();

        if(sBackgroundFlutterEngine == null) {
            callbackHandler = context.getSharedPreferences(
                    CallerPlugin.PLUGIN_NAME,
                    Context.MODE_PRIVATE
            ).getLong(CallerPlugin.CALLBACK_SHAREDPREFERENCES_KEY, 0);

            callbackHandlerUser = context.getSharedPreferences(
                    CallerPlugin.PLUGIN_NAME,
                    Context.MODE_PRIVATE
            ).getLong(CallerPlugin.CALLBACK_USER_SHAREDPREFERENCES_KEY, 0);

            if (callbackHandler == 0L || callbackHandlerUser == 0L) {
                Log.e(CallerPlugin.PLUGIN_NAME, "Fatal: No callback registered");
                return;
            }

            Log.d(CallerPlugin.PLUGIN_NAME, "Found callback handler " + callbackHandler);
            Log.d(CallerPlugin.PLUGIN_NAME, "Found user callback handler " + callbackHandlerUser);

            // Retrieve the actual callback information needed to invoke it.
            FlutterCallbackInformation callbackInfo = FlutterCallbackInformation.lookupCallbackInformation(callbackHandler);
            if (callbackInfo == null) {
                Log.e(CallerPlugin.PLUGIN_NAME, "Fatal: failed to find callback");
                return;
            }

            sBackgroundFlutterEngine = new FlutterEngine(context);
            DartExecutor.DartCallback args = new DartExecutor.DartCallback(
                    context.getAssets(),
                    this.flutterLoader.findAppBundlePath(),
                    callbackInfo
            );

            // Start running callback dispatcher code in our background FlutterEngine instance.
            sBackgroundFlutterEngine.getDartExecutor().executeDartCallback(args);
        }

        arguments.add(callbackHandler);
        arguments.add(callbackHandlerUser);
        arguments.add(incomingNumber);

        // Create the MethodChannel used to communicate between the callback
        // dispatcher and this GeofencingService instance.
        channel = new MethodChannel(
                sBackgroundFlutterEngine.getDartExecutor().getBinaryMessenger(),
                CallerPlugin.PLUGIN_NAME + "_background"
        );

        switch (state) {
            case TelephonyManager.CALL_STATE_IDLE:
                if(previousState == TelephonyManager.CALL_STATE_OFFHOOK){
                    ZonedDateTime now = ZonedDateTime.now();
                    Duration duration = Duration.between(time, now);

                    Log.d(CallerPlugin.PLUGIN_NAME, "Phone State event CALL_ENDED with number " + incomingNumber);
                    arguments.add("callEnded");
                    arguments.add(duration.toMillis() / 1000);

                    time = null;
                    channel.invokeMethod("incomingCallEnded", arguments);
                } else if(previousState == TelephonyManager.CALL_STATE_RINGING){
                    Log.d(CallerPlugin.PLUGIN_NAME, "Phone State event INCOMING_CALL_MISSED " + incomingNumber);
                    arguments.add("onMissedCall");
                    arguments.add(null);
                    channel.invokeMethod("onMissedCall", arguments);
                }

                previousState = TelephonyManager.CALL_STATE_IDLE;
                break;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                if(previousState == TelephonyManager.CALL_STATE_RINGING){
                    Log.d(CallerPlugin.PLUGIN_NAME, "Phone State event INCOMING_CALL_ANSWERED " + incomingNumber);
                    arguments.add("onIncomingCallAnswered");
                    arguments.add(null);
                    channel.invokeMethod("onIncomingCallAnswered", arguments);
                }
                previousState = TelephonyManager.CALL_STATE_OFFHOOK;

                // Get the current time to count later the duration of the call
                time = ZonedDateTime.now();
                break;
            case TelephonyManager.CALL_STATE_RINGING:
                if(previousState == TelephonyManager.CALL_STATE_IDLE){
                    Log.d(CallerPlugin.PLUGIN_NAME, "Phone State event INCOMING_CALL_RECEIVED " + incomingNumber);
                    arguments.add("onIncomingCallReceived");
                    arguments.add(null);
                    channel.invokeMethod("onIncomingCallReceived", arguments);
                }
                previousState = TelephonyManager.CALL_STATE_RINGING;
                break;
        }
    }
}

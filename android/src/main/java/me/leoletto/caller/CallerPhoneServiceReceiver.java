package me.leoletto.caller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import io.flutter.embedding.engine.loader.FlutterLoader;

public class CallerPhoneServiceReceiver extends BroadcastReceiver  {
    private TelephonyManager telephony;
    private static CallerPhoneStateListener callerPhoneStateListener;

    public void onReceive(Context context, Intent intent) {
        Log.d(CallerPlugin.PLUGIN_NAME, "New broadcast event received");

        if(callerPhoneStateListener == null) {
            FlutterLoader flutterLoader = new FlutterLoader();
            flutterLoader.startInitialization(context);
            flutterLoader.ensureInitializationComplete(context, null);

            callerPhoneStateListener = new CallerPhoneStateListener(context, flutterLoader);

            telephony = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            telephony.listen(callerPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }
}

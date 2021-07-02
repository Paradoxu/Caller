package me.leoletto.caller;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;

// https://stackoverflow.com/questions/50187680/how-to-call-methods-in-dart-portion-of-the-app-from-the-native-platform-using-m
/** CallerPlugin */
public class CallerPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.RequestPermissionsResultListener {
  public static String PLUGIN_NAME = "me.leoletto.caller";
  public static String CALLBACK_SHAREDPREFERENCES_KEY = "callerPluginCallbackHandler";
  public static String CALLBACK_USER_SHAREDPREFERENCES_KEY = "callerPluginCallbackHandlerUser";

  private MethodChannel channel;
  private Activity currentActivity;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "me.leoletto.caller");
    channel.setMethodCallHandler(this);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    ArrayList<Object> arguments = (ArrayList<Object>)call.arguments;

    if (call.method.equals("initialize") && arguments.size() == 2) {
      if(!doCheckPermission()) {
        result.error("MISSING_PERMISSION", null, null);
        return;
      }

      SharedPreferences sharedPref = currentActivity.getSharedPreferences(PLUGIN_NAME, Context.MODE_PRIVATE);
      SharedPreferences.Editor editor = sharedPref.edit();
      editor.putLong(CALLBACK_SHAREDPREFERENCES_KEY, (Long)arguments.get(0));
      editor.putLong(CALLBACK_USER_SHAREDPREFERENCES_KEY, (Long)arguments.get(1));
      editor.commit();

      Context context = currentActivity.getApplicationContext();
      ComponentName receiver = new ComponentName(context, CallerPhoneServiceReceiver.class);
      PackageManager packageManager = context.getPackageManager();

      packageManager.setComponentEnabledSetting(receiver,
              PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
              PackageManager.DONT_KILL_APP
      );

      Log.d("me.leoletto.caller", "Service initialized");
      result.success(true);
      
    } else if(call.method.equals("stopCaller")) {
      SharedPreferences sharedPref = currentActivity.getSharedPreferences(PLUGIN_NAME, Context.MODE_PRIVATE);
      SharedPreferences.Editor editor = sharedPref.edit();
      editor.remove(CALLBACK_SHAREDPREFERENCES_KEY);
      editor.remove(CALLBACK_USER_SHAREDPREFERENCES_KEY);

      Context context = currentActivity.getApplicationContext();
      ComponentName receiver = new ComponentName(context, CallerPhoneServiceReceiver.class);
      PackageManager packageManager = context.getPackageManager();

      packageManager.setComponentEnabledSetting(receiver,
              PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
              PackageManager.DONT_KILL_APP
      );

      editor.commit();

      result.success(true);
    } else if (call.method.equals("requestPermissions")) {
      requestPermissions();
    } else if (call.method.equals("checkPermissions")) {
      boolean check = doCheckPermission();
      result.success(check);
    } else {
      result.notImplemented();
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
  }

  private boolean doCheckPermission() {
    if(currentActivity != null && currentActivity.getApplicationContext() != null){
      int permPhoneState = ContextCompat.checkSelfPermission(currentActivity, Manifest.permission.READ_PHONE_STATE);
      int permReadCallLog = ContextCompat.checkSelfPermission(currentActivity, Manifest.permission.READ_CALL_LOG);
      int grantedCode = PackageManager.PERMISSION_GRANTED;

      return permPhoneState == grantedCode && permReadCallLog == grantedCode;
    }
    return false;
  }

  private void requestPermissions() {
    if(currentActivity != null && currentActivity.getApplicationContext() != null){
      int permPhoneState = ContextCompat.checkSelfPermission(currentActivity, Manifest.permission.READ_PHONE_STATE);
      int permReadCallLog = ContextCompat.checkSelfPermission(currentActivity, Manifest.permission.READ_CALL_LOG);
      int grantedCode = PackageManager.PERMISSION_GRANTED;

      if(permPhoneState != grantedCode || permReadCallLog != grantedCode){
        String[] permissions = {
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG
        };
        ActivityCompat.requestPermissions(currentActivity, permissions, 999);
      }
    }
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    currentActivity = binding.getActivity();
    binding.addRequestPermissionsResultListener(this);
    requestPermissions();
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    currentActivity = binding.getActivity();
    binding.addRequestPermissionsResultListener(this);
    requestPermissions();
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    currentActivity = null;
  }

  @Override
  public void onDetachedFromActivity() {
    currentActivity = null;
  }

  @Override
  public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    switch(requestCode){
      case 999:
        if (grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
          return true;
        } else {
          return false;
        }
      default:
        return false;
    }
  }
}

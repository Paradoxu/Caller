package me.leoletto.caller

import android.Manifest

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import android.content.pm.PackageManager

import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding

import androidx.core.app.ActivityCompat

import androidx.core.content.ContextCompat

import android.content.ComponentName

import android.app.Activity
import android.content.Context
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding

import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.plugin.common.PluginRegistry


/** CallerPlugin */
class CallerPlugin : FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.RequestPermissionsResultListener {
  companion object {
    const val PLUGIN_NAME = "me.leoletto.caller"
    const val CALLBACK_SHAREDPREFERENCES_KEY = "callerPluginCallbackHandler"
    const val CALLBACK_USER_SHAREDPREFERENCES_KEY = "callerPluginCallbackHandlerUser"
  }

  private var channel: MethodChannel? = null
  private var currentActivity: Activity? = null

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, PLUGIN_NAME)
    channel!!.setMethodCallHandler(this)
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    val arguments = call.arguments as ArrayList<*>?
    if (call.method == "initialize" && arguments?.size == 2) {
      if (!doCheckPermission()) {
        result.error("MISSING_PERMISSION", null, null)
        return
      }
      val sharedPref = currentActivity!!.getSharedPreferences(PLUGIN_NAME, Context.MODE_PRIVATE)
      val editor = sharedPref.edit()
      editor.putLong(CALLBACK_SHAREDPREFERENCES_KEY, (arguments[0] as Long))
      editor.putLong(CALLBACK_USER_SHAREDPREFERENCES_KEY, (arguments[1] as Long))
      editor.commit()

      val context = currentActivity!!.applicationContext
      val receiver = ComponentName(context, CallerPhoneServiceReceiver::class.java)
      context.packageManager.setComponentEnabledSetting(receiver,
              PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
              PackageManager.DONT_KILL_APP
      )
      Log.d(PLUGIN_NAME, "Service initialized")
      Log.d(PLUGIN_NAME, receiver.toString())
      result.success(true)

    } else if (call.method == "stopCaller") {
      val sharedPref = currentActivity!!.getSharedPreferences(PLUGIN_NAME, Context.MODE_PRIVATE)
      val editor = sharedPref.edit()
      editor.remove(CALLBACK_SHAREDPREFERENCES_KEY)
      editor.remove(CALLBACK_USER_SHAREDPREFERENCES_KEY)
      val context: Context = currentActivity!!.applicationContext
      val receiver = ComponentName(context, CallerPhoneServiceReceiver::class.java)
      val packageManager: PackageManager = context.packageManager
      packageManager.setComponentEnabledSetting(receiver,
              PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
              PackageManager.DONT_KILL_APP
      )
      editor.commit()
      result.success(true)

    } else if (call.method == "requestPermissions") {
      Log.d(PLUGIN_NAME, "Requesting permission")
      requestPermissions()

    } else if (call.method == "checkPermissions") {
      val check = doCheckPermission()
      Log.d(PLUGIN_NAME, "Permission checked: $check")
      result.success(check)

    } else {
      result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPluginBinding) {
    channel!!.setMethodCallHandler(null)
  }

  private fun doCheckPermission(): Boolean {
    if (currentActivity != null && currentActivity!!.applicationContext != null) {
      val permPhoneState = ContextCompat.checkSelfPermission(currentActivity!!, Manifest.permission.READ_PHONE_STATE)
      val permReadCallLog = ContextCompat.checkSelfPermission(currentActivity!!, Manifest.permission.READ_CALL_LOG)
      val grantedCode = PackageManager.PERMISSION_GRANTED
      return permPhoneState == grantedCode && permReadCallLog == grantedCode
    }
    return false
  }

  private fun requestPermissions() {
    if (currentActivity != null && currentActivity!!.applicationContext != null) {
      val permPhoneState = ContextCompat.checkSelfPermission(currentActivity!!, Manifest.permission.READ_PHONE_STATE)
      val permReadCallLog = ContextCompat.checkSelfPermission(currentActivity!!, Manifest.permission.READ_CALL_LOG)
      val grantedCode = PackageManager.PERMISSION_GRANTED
      if (permPhoneState != grantedCode || permReadCallLog != grantedCode) {
        val permissions = arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG
        )
        ActivityCompat.requestPermissions(currentActivity!!, permissions, 999)
      }
    }
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    currentActivity = binding.activity
    binding.addRequestPermissionsResultListener(this)
    requestPermissions()
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    currentActivity = binding.activity
    binding.addRequestPermissionsResultListener(this)
    requestPermissions()
  }

  override fun onDetachedFromActivityForConfigChanges() {
    currentActivity = null
  }

  override fun onDetachedFromActivity() {
    currentActivity = null
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>?, grantResults: IntArray?): Boolean {
    return when (requestCode) {
      999 -> grantResults != null && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
      else -> false
    }
  }
}
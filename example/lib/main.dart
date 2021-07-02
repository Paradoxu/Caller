import 'package:caller/caller.dart';
import 'package:flutter/material.dart';

/// Defines a callback that will handle all background incoming events
///
/// The duration will only have a value if the current event is `CallerEvent.callEnded`
Future<void> callerCallbackHandler(
  CallerEvent event,
  String number,
  int? duration,
) async {
  print("New event received from native $event");
  switch (event) {
    case CallerEvent.callEnded:
      print('Ended a call with number $number and duration $duration');
      break;
    case CallerEvent.onMissedCall:
      print('Missed a call from number $number');
      break;
    case CallerEvent.onIncomingCallAnswered:
      print('Accepted call from number $number');
      break;
    case CallerEvent.onIncomingCallReceived:
      print('Phone is ringing with number $number');
      break;
  }
}

Future<void> initialize() async {
  /// Check if the user has granted permissions
  final permission = await Caller.checkPermission();

  /// If not, then request user permission to access the Call State
  if (!permission)
    Caller.requestPermissions();
  else
    Caller.initialize(callerCallbackHandler);
}

void main() {
  initialize();

  /// Run your app as you would normally do...
  /// runApp(MyApp());
}

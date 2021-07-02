abstract class CallerHandler {
  void onIncomingCallReceived();
  void onIncomingCallAnswered();
  void onIncomingCallEnded();
  void onOutgoingCallStarted();
  void onOutgoingCallEnded();
  void onMissedCall();
}

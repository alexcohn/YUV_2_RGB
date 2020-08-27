import 'package:camera/camera.dart';

/// Contains the cameras of the device. It is loaded in the main() function of the app.
List<CameraDescription> cameras = [];

mixin CameraHandler {
  /// Camera index in [cameras]. 0 would properly be the back camera of the device.
  int cameraType = 0;
  CameraController controller;

  void showCameraException(CameraException e) {
    logError(e.code, e.description);
    print('Error: ${e.code}\n${e.description}');
  }

  void logError(String code, String message) =>
      print('Error: $code\nError Message: $message');
}

import 'dart:async';

import 'package:camera/camera.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:yuvtransform/camera_handler.dart';
import 'package:yuvtransform/service/image_result_processor_service.dart';

import 'camera_screen.dart';

/// Delay for image capture in stream. Should properly be in some sort of SettingsManager in a real project.
const DELAY_TIME = 3000;

class YuvTransformScreen extends StatefulWidget {
  @override
  _YuvTransformScreenState createState() => _YuvTransformScreenState();
}

class _YuvTransformScreenState extends State<YuvTransformScreen>
    with CameraHandler, WidgetsBindingObserver {
  List<StreamSubscription> _subscription = List();
  ImageResultProcessorService _imageResultProcessorService;
  bool _isProcessing = false;
  var _capturedImage = new Image();

  @override
  void initState() {
    super.initState();
    // Registers the page to observer for life cycle managing.
    _imageResultProcessorService = ImageResultProcessorService();
    WidgetsBinding.instance.addObserver(this);
    _subscription.add(_imageResultProcessorService.queue.listen((event) {
      _isProcessing = false;
      setState(() {
        _capturedImage = Image.memory(event, width: 180, height: 180);
      });
    }));
    for (CameraDescription camera in  cameras) {
      print("${camera.lensDirection} orientation: ${camera.sensorOrientation}");
    }
    onNewCameraSelected(cameras[0]);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    // Dispose all streams!
    _subscription.forEach((element) {
      element.cancel();
    });
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // App state changed before we got the chance to initialize.
    if (controller == null || !controller.value.isInitialized) {
      return;
    }

    if (state == AppLifecycleState.inactive) {
      controller?.dispose();
    } else if (state == AppLifecycleState.resumed) {
      if (controller != null) {
        onNewCameraSelected(controller.description);
      }
    }
  }

  void onNewCameraSelected(CameraDescription cameraDescription) async {
    if (controller != null) {
      await controller?.dispose();
    }
    controller = CameraController(
      cameraDescription,
      ResolutionPreset.medium,
      enableAudio: false,
    );

    // If the controller is updated then update the UI.
    controller.addListener(() {
      if (mounted) setState(() {});
      if (controller.value.hasError) {
        print("Camera error: ${controller.value.errorDescription}");
      }
    });

    try {
      await controller.initialize();

      await controller
          .startImageStream((CameraImage image) => _processCameraImage(image));
    } on CameraException catch (e) {
      showCameraException(e);
    }

    if (mounted) {
      setState(() {});
    }
  }

  void _processCameraImage(CameraImage image) async {
    if (_isProcessing)
      return; //Do not detect another image until you finish the previous.
    _isProcessing = true;
    print("Sent a new image and sleeping for: $DELAY_TIME");
    await Future.delayed(Duration(milliseconds: DELAY_TIME),
        () => _imageResultProcessorService.addRawImage(image, luminanceOnly: true));
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: <Widget> [
        CameraScreenWidget(
          controller: controller
        ),
        Positioned(
          bottom: 0,
          width: 180,
          height: 180,
          child: _capturedImage,
        )
      ]
    );
  }
}

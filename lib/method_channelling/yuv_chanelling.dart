import 'dart:typed_data';

import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// constants from android.view.Surface
enum Rotation {
  ROTATION_0,
  ROTATION_90,
  ROTATION_180,
  ROTATION_270,
}

class YuvChannelling {
  MethodChannel platform =
      const MethodChannel('tomer.blecher.yuv_transform/yuv');

  ///  Transform given image to JPEG compressed through native code.
  ///
  ///  Function gets [CameraImage] in YUV format for processing and returns
  ///  [Uint8List] of JPEG or BMP bytes.
  ///  optional rotation may be 0, 90, 180, or 270
  ///
  Future<Uint8List> yuv_transform(CameraImage image, {bool compress=true, Rotation rotation=Rotation.ROTATION_90}) async {
    List<int> strides = new Int32List(image.planes.length * 2);
    int index = 0;
    // We need to transform the image to Uint8List so that the native code could
    // transform it to byte[]
    List<Uint8List> planes = image.planes.map((plane) {
      strides[index] = (plane.bytesPerRow);
      index++;
      strides[index] = (plane.bytesPerPixel);
      index++;
      return plane.bytes;
    }).toList();
    return await platform.invokeMethod('yuv_transform', {
      'planes': planes,
      'height': image.height,
      'width': image.width,
      'strides': strides,
      'compress': compress,
      'rotation': rotation.index,
    });
  }
}

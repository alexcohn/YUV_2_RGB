import 'dart:typed_data';

import 'package:camera/camera.dart';
import 'package:rxdart/subjects.dart';
import 'package:yuvtransform/method_channelling/yuv_chanelling.dart';
import 'package:image/image.dart';

class ImageResultProcessorService  {
  YuvChannelling _yuvChannelling = YuvChannelling();
  /// We need to notify the page that we have finished the process of the image.
  /// The subject could possibly sink the result [Uint8List] if needed.
  PublishSubject<Uint8List> _queue = PublishSubject();
  /// Observers that needs the result image should subscribe to this stream.
  Stream<Uint8List> get queue => _queue.stream;

  num lastTimeStamp = 0;
  num frameCount = 0;

  addRawImage(CameraImage cameraImage, { bool luminanceOnly = false}) async {
    frameCount += 1;
    num newTimeStamp = DateTime.now().millisecondsSinceEpoch;
    if (luminanceOnly) {
      print("Image.fromBytes(format: luminance) ${cameraImage.width}x${cameraImage.height}: ${cameraImage.width*cameraImage.height} ${cameraImage.planes[0].bytes.length} ${cameraImage.planes[0].bytesPerRow} ");
      Image image = Image.fromBytes(cameraImage.width, cameraImage.height, cameraImage.planes[0].bytes, format: Format.luminance, channels: Channels.rgb );
      num beforeJpeg = DateTime.now().millisecondsSinceEpoch;
      print("Image.fromBytes(format: ${cameraImage.format.group}) took ${beforeJpeg - newTimeStamp} ms");
      Uint8List imgJpeg = await encodeJpg(image);
      print("encodeJpg ${cameraImage.width}x${cameraImage.height} took ${DateTime.now().millisecondsSinceEpoch - beforeJpeg} ms");
      _queue.sink.add(imgJpeg);
    }
    else {
      Uint8List imgJpeg = await _yuvChannelling.yuv_transform(cameraImage);
      _queue.sink.add(imgJpeg);
    }
    print("Job ${frameCount} took ${DateTime.now().millisecondsSinceEpoch - newTimeStamp} ms to complete" + (lastTimeStamp > 0 ? ", at ${(1000.0/(newTimeStamp-lastTimeStamp)).toStringAsFixed(1)} fps" : ""));
    lastTimeStamp = newTimeStamp;
  }

  void dispose() {
    _queue.close();
  }

}
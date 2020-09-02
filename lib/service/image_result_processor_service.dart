import 'dart:typed_data';

import 'package:camera/camera.dart';

import 'package:rxdart/subjects.dart';
import 'package:yuvtransform/method_channelling/yuv_chanelling.dart';

class ImageResultProcessorService  {
  YuvChannelling _yuvChannelling = YuvChannelling();
  /// We need to notify the page that we have finished the process of the image.
  /// The subject could possibly sink the result [Uint8List] if needed.
  PublishSubject<Uint8List> _queue = PublishSubject();
  /// Observers that needs the result image should subscribe to this stream.
  Stream<Uint8List> get queue => _queue.stream;

  num lastTimeStamp = 0;
  num totalTime = 0;
  num frameCount = 0;

  addRawImage(CameraImage cameraImage, { bool luminanceOnly = false}) async {
    frameCount += 1;
    num newTimeStamp = DateTime.now().millisecondsSinceEpoch;
    if (luminanceOnly) {
      BMP8Header bmp;
      int widx = 0;

      var it = cameraImage.planes[0].bytes.iterator;
      bmp = BMP8Header(cameraImage.width, cameraImage.height);
      for (num row = 0; row < bmp.height; row++) {
        for (num col = 0; col < bmp.width; col++) {
          it.moveNext();
          bmp.bd.setUint8(widx++, it.current);
        }
        for (num col = bmp.width; col < cameraImage.planes[0].bytesPerRow; col++) {
          it.moveNext();
        }
      }
      _queue.sink.add(bmp.list);
    }
    else {
      Uint8List img = await _yuvChannelling.yuv_transform(cameraImage, compress: compress, rotation: rotation);
      _queue.sink.add(img);
    }
    totalTime += DateTime.now().millisecondsSinceEpoch - newTimeStamp;
    print("Job ${frameCount} took ${DateTime.now().millisecondsSinceEpoch - newTimeStamp}/${(totalTime/frameCount).toStringAsFixed(1)} ms to complete" + (lastTimeStamp > 0 ? ", at ${(1000.0/(newTimeStamp-lastTimeStamp)).toStringAsFixed(1)} fps" : ""));
    lastTimeStamp = newTimeStamp;
  }

  void dispose() {
    _queue.close();
  }

}

class BMP8Header {
  int width; // NOTE: width must be multiple of 4 as no account is made for bitmap padding
  int height;

  Uint8List list;
  ByteData bd;
  int _totalHeaderSize;

  BMP8Header(this.width, this.height) : assert(width & 3 == 0) {
    int baseHeaderSize = 54;
    _totalHeaderSize = baseHeaderSize + 1024; // base + color map
    int fileLength = _totalHeaderSize + width * height * 3; // header + bitmap
    list = new Uint8List(fileLength);
    bd = list.buffer.asByteData(0, _totalHeaderSize);
    bd.setUint8(0, 0x42);
    bd.setUint8(1, 0x4d);
    bd.setUint32(2, fileLength, Endian.little); // file length
    bd.setUint32(10, _totalHeaderSize, Endian.little); // start of the bitmap
    bd.setUint32(14, 40, Endian.little); // info header size
    bd.setUint32(18, width, Endian.little);
    bd.setInt32(22, -height, Endian.little);
    bd.setUint16(26, 1, Endian.little); // planes
    bd.setUint16(28, 8, Endian.little); // bpp
    // leave everything else as zero

    // color table; [0] is bright green for debugging
    int offset = baseHeaderSize;
    bd.setUint8(offset++, 0); // B
    bd.setUint8(offset++, 255); // G
    bd.setUint8(offset++, 0); // R
    bd.setUint8(offset++, 255); // A
    for (int rgb = 1; rgb < 256; rgb++) {
      bd.setUint8(offset++, rgb); // B
      bd.setUint8(offset++, rgb); // G
      bd.setUint8(offset++, rgb); // R
      bd.setUint8(offset++, 255); // A
    }

    bd = list.buffer.asByteData(_totalHeaderSize);
  }
}
package tomerblecher.yuvtransform;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.List;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugins.GeneratedPluginRegistrant;

public class MainActivity extends FlutterActivity {
    // Set a name for the method chanel.
    // This name is a key for the Flutter MethodChannel and needs to be equal to the name configured at the dart part
    private static final String CHANNEL = "tomer.blecher.yuv_transform/yuv";

    private long total_bitmap = 0;
    private long total_convert = 0;
    private long total_calls = 0;
    private YuvConverter yuvConverter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        if (yuvConverter != null) yuvConverter.close();
        super.onDestroy();
    }

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        GeneratedPluginRegistrant.registerWith(flutterEngine);
        System.out.println("JAVA REGISTERED ");
        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
                .setMethodCallHandler(
                        (call, result) -> {
                            switch (call.method) {
                                case "yuv_transform": {
                                    List<byte[]> bytesList = call.argument("planes");
                                    int[] strides = call.argument("strides");
                                    int width = call.argument("width");
                                    int height = call.argument("height");
                                    boolean compress = call.argument("compress");
                                    int rotation = call.argument("rotation");

                                    result.success(yuvTransform(bytesList, strides, width, height, compress, rotation));
                                    break;
                                }
                            }
                        }
                );

    }

    private byte[] yuvTransform(List<byte[]> bytesList, int[] strides, int width, int height, boolean compress, @YuvConverter.Rotation int rotation) {
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            int _tmp = width;
            width = height;
            height = _tmp;
        }
        if (total_calls == 0) {
            long start = new Date().getTime();
            yuvConverter = new YuvConverter(getApplicationContext(), bytesList.get(0).length, bytesList.get(1) == null ? 0 : bytesList.get(1).length, width, height);
            Log.i("flutter ", "yuv_transform init YuvConverter in " + ((new Date().getTime() - start)) + " ms");
        }

        total_calls += 1;
        long startTime = new Date().getTime();
        try {
            /*
             * hardcoded assuptions:
             *   stride[0] (yLine) >= width
             *   stride[1] (yPixel) == 1
             *   stride[2] (uLine) >= width
             *   stride[3] (uPixel) == 2
             *   stride[4] (vLine) == uLine
             *   stride[5] (vPixel) == uPixel
             */
            Bitmap abgr = yuvConverter.YUV420toRGB(bytesList.get(0), bytesList.get(1), bytesList.get(2),
                    strides[0], strides[2], width, height, rotation);

            long bitmapTime = new Date().getTime() - startTime;
            total_bitmap += bitmapTime;
            Log.i("flutter ", "yuv_transform bitmap " + width + "x" + height + " in " + (new Date().getTime() - startTime) + " ms, average "  + total_bitmap /total_calls);

            if (!compress) {
                int bitmapHeaderSize = 54 + 16;
                int bmpSize = bitmapHeaderSize + abgr.getWidth() * abgr.getHeight() * 4;
                ByteBuffer byteBuffer = ByteBuffer.allocate(bmpSize);
                byte[] byteArray = byteBuffer.array();

                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                byteBuffer.put((byte)'B');
                byteBuffer.put((byte)'M');
                byteBuffer.putLong(byteBuffer.capacity());
                byteBuffer.putInt(bitmapHeaderSize);
                byteBuffer.putInt(40 + 16); // info header size
                byteBuffer.putInt(width);
                byteBuffer.putInt(-height);
                byteBuffer.putShort((short)1); // planes
                byteBuffer.putShort((short)32); // bpp
                byteBuffer.putInt(3); // BI_BITFIELDS
                byteBuffer.putInt(32);
                byteBuffer.position(54); // set XBGR
                byteBuffer.putInt(0xff);     // R
                byteBuffer.putInt(0xff00);   // G
                byteBuffer.putInt(0xff0000); // B
                byteBuffer.putInt(0x0);      // A
                byteBuffer.position(bitmapHeaderSize);
                abgr.copyPixelsToBuffer(byteBuffer);

                total_convert += new Date().getTime() - startTime;
                Log.i("flutter ", "yuv_transform to byte[] " + (new Date().getTime() - bitmapTime - startTime) + " average " + (total_convert - total_bitmap) / total_calls + " ms");

                if (total_calls == 4) {
                    FileOutputStream bmpStream = new FileOutputStream(new File(getCacheDir(), "rotated.bmp"));
                    bmpStream.write(byteArray);
                    bmpStream.close();
                }

                return byteArray;
            }

            try (ByteArrayOutputStream outputStreamCompressed = new ByteArrayOutputStream()) {
                abgr.compress(Bitmap.CompressFormat.JPEG, 80, outputStreamCompressed);

                total_convert += new Date().getTime() - startTime;
                Log.i("flutter ", "yuv_transform jpeg compression " + (new Date().getTime() - bitmapTime - startTime) + " average " + (total_convert - total_bitmap) / total_calls + " ms");

                if (total_calls == 10) {
                    FileOutputStream jpg = new FileOutputStream(new File(getCacheDir(), "rotated.jpg"));
                    jpg.write(outputStreamCompressed.toByteArray());
                    jpg.close();
                    Log.i("flutter ", "yuv_transform rotated Jpeg saved to " + new File(getCacheDir(), "rotated.jpg"));
                }

                return outputStreamCompressed.toByteArray();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}

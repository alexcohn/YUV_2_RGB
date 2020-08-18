package tomerblecher.yuvtransform;

import android.content.Context;
import android.graphics.*;
import android.renderscript.*;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;

public class YuvConverter implements AutoCloseable {
    /**
     * Converts an NV21 image into JPEG compressed.
     * @param nv21 byte[] of the input image in NV21 format
     * @param width Width of the image.
     * @param height Height of the image.
     * @param quality Quality of compressed image(0-100)
     * @return byte[] of a compressed Jpeg image.
     */
    public static byte[] NV21toJPEG(byte[] nv21, int width, int height, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        yuv.compressToJpeg(new Rect(0, 0, width, height), quality, out);
        return out.toByteArray();
    }

    /**
     * Converts an NV21 image into Bitmap.
     * @param nv21 byte[] of the input image in NV21 format
     * @param width Width of the image.
     * @param height Height of the image.
     * @return RGBA_8888 Bitmap image.
     */
    public static Bitmap NV21toRGB(Context ctx, byte[] nv21, int width, int height) {
        RenderScript rs = RenderScript.create(ctx);
        Type.Builder yuvBlder = new Type.Builder(rs, Element.U8(rs)).setX(width).setY(height*3/2);
        Allocation allocIn = Allocation.createTyped(rs, yuvBlder.create(), Allocation.USAGE_SCRIPT);
        Type rgbType = Type.createXY(rs, Element.RGBA_8888(rs), width, height);
        Allocation allocOut = Allocation.createTyped(rs, rgbType, Allocation.USAGE_SCRIPT);

        ScriptC_yuv2rgb scriptC_yuv2rgb = new ScriptC_yuv2rgb(rs);
        allocIn.copyFrom(nv21);
        scriptC_yuv2rgb.set_Width(width);
        scriptC_yuv2rgb.set_Height(height);
        scriptC_yuv2rgb.set_NV21(allocIn);
        scriptC_yuv2rgb.forEach_NV21toRGB(allocOut);

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        allocOut.copyTo(bmp);

        allocIn.destroy();
        scriptC_yuv2rgb.destroy();
        return bmp;
    }

    /**
     * Converts an YUV_420 image into Bitmap.
     * @param planes  List of Bytes list
     * @param strides contains the strides of each plane. The structure :
     *                strideRowFirstPlane, stridePixelFirstPlane,
     *                strideRowSecondPlane, stridePixelSecondPlane,
     *                strideRowThirdPlane, stridePixelThirdPlane,
     * @param width Width of the image.
     * @param height Height of the image.
     * @return RGBA_8888 Bitmap image.
     *
     * assuptions:
     *   stride[0] (Y_line) >= width
     *   stride[1] (Y_pixel) == 1
     *   stride[2] (U_line) >= width
     *   stride[3] (U_pixel) == 2
     *   stride[4] (V_line) == U_line
     *   stride[5] (V_pixel) == U_pixel
     */
    public static Bitmap YUV420toRGB(Context ctx, List<byte[]> planes, int[] strides, int width, int height) {
        RenderScript rs = RenderScript.create(ctx);
        Type.Builder yBuilder = new Type.Builder(rs, Element.U8(rs)).setX(planes.get(0).length);
        Allocation allocY = Allocation.createTyped(rs, yBuilder.create(), Allocation.USAGE_SCRIPT);
        Type.Builder uvBuilder = new Type.Builder(rs, Element.U8(rs)).setX(planes.get(1).length);
        Allocation allocU = Allocation.createTyped(rs, uvBuilder.create(), Allocation.USAGE_SCRIPT);
        Allocation allocV = Allocation.createTyped(rs, uvBuilder.create(), Allocation.USAGE_SCRIPT);
        Type rgbType = Type.createXY(rs, Element.RGBA_8888(rs), width, height);
        Allocation allocOut = Allocation.createTyped(rs, rgbType, Allocation.USAGE_SCRIPT);

        ScriptC_yuv2rgb scriptC_yuv2rgb = new ScriptC_yuv2rgb(rs);
        allocY.copyFrom(planes.get(0));
        allocU.copyFrom(planes.get(1));
        allocV.copyFrom(planes.get(2));
        scriptC_yuv2rgb.set_Width(width);
        scriptC_yuv2rgb.set_Height(height);
        scriptC_yuv2rgb.set_Yline(strides[0]);
        scriptC_yuv2rgb.set_UVline(strides[2]);
        scriptC_yuv2rgb.set_Yplane(allocY);
        scriptC_yuv2rgb.set_Uplane(allocU);
        scriptC_yuv2rgb.set_Vplane(allocV);
        scriptC_yuv2rgb.forEach_YUV420toRGB(allocOut);

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        allocOut.copyTo(bmp);

        allocY.destroy();
        allocU.destroy();
        allocV.destroy();
        scriptC_yuv2rgb.destroy();
        return bmp;
    }

    /**
     * Format YUV_420 planes in to NV21.
     * Removes strides from planes and combines the result to single NV21 byte array.
     * @param planes  List of Bytes list
     * @param strides contains the strides of each plane. The structure :
     *                strideRowFirstPlane,stridePixelFirstPlane, strideRowSecondPlane
     * @param width   Width of the image
     * @param height  Height of given image
     * @return NV21 image byte[].
     */
    public static byte[] YUVtoNV21 (List<byte[]> planes, int[] strides, int width, int height) {
        Rect crop = new Rect(0, 0, width, height);
        int format = ImageFormat.YUV_420_888;
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[strides[0]];
        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.size(); i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    channelOffset = width * height + 1;
                    outputStride = 2;
                    break;
                case 2:
                    channelOffset = width * height;
                    outputStride = 2;
                    break;
            }

            ByteBuffer buffer = ByteBuffer.wrap(planes.get(i));
            int rowStride;
            int pixelStride;
            if(i ==0 ) {
                rowStride = strides[i];
                pixelStride = strides[i+1];
            }
            else {
                rowStride = strides[i *2 ];
                pixelStride = strides[i *2 +1];
            }
            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
        }
        return data;

    }

    // non-static API

    private RenderScript rs;
    private ScriptC_yuv2rgb scriptC_yuv2rgb;

    YuvConverter(Context ctx) {
        rs = RenderScript.create(ctx);
        scriptC_yuv2rgb = new ScriptC_yuv2rgb(rs);
    }

    public void close() {
        scriptC_yuv2rgb.destroy();
    }

    public Bitmap YUV420toRGB(byte[] yPlane, byte[] uPlane, byte[] vPlane,
                              int yLine, int uLine, int width, int height) {
        Type.Builder yBuilder = new Type.Builder(rs, Element.U8(rs)).setX(yPlane.length);
        Allocation allocY = Allocation.createTyped(rs, yBuilder.create(), Allocation.USAGE_SCRIPT);
        Type.Builder uvBuilder = new Type.Builder(rs, Element.U8(rs)).setX(uPlane.length);
        Allocation allocU = Allocation.createTyped(rs, uvBuilder.create(), Allocation.USAGE_SCRIPT);
        Allocation allocV = Allocation.createTyped(rs, uvBuilder.create(), Allocation.USAGE_SCRIPT);
        Type rgbType = Type.createXY(rs, Element.RGBA_8888(rs), width, height);
        Allocation allocOut = Allocation.createTyped(rs, rgbType, Allocation.USAGE_SCRIPT);

        allocY.copyFrom(yPlane);
        allocU.copyFrom(uPlane);
        allocV.copyFrom(vPlane);
        scriptC_yuv2rgb.set_Width(width);
        scriptC_yuv2rgb.set_Height(height);
        scriptC_yuv2rgb.set_Yline(yLine);
        scriptC_yuv2rgb.set_UVline(uLine);
        scriptC_yuv2rgb.set_Yplane(allocY);
        scriptC_yuv2rgb.set_Uplane(allocU);
        scriptC_yuv2rgb.set_Vplane(allocV);
        scriptC_yuv2rgb.forEach_YUV420toRGB(allocOut);

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        allocOut.copyTo(bmp);

        allocY.destroy();
        allocU.destroy();
        allocV.destroy();
        return bmp;
    }

}

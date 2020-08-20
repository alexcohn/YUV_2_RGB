package tomerblecher.yuvtransform;

import android.content.Context;
import android.graphics.*;
import android.renderscript.*;
import android.view.Surface;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class YuvConverter implements AutoCloseable {

    private RenderScript rs;
    private ScriptC_yuv2rgb scriptC_yuv2rgb;
    private Bitmap bmp;

    YuvConverter(Context ctx, int ySize, int uvSize, int width, int height) {
        rs = RenderScript.create(ctx);
        scriptC_yuv2rgb = new ScriptC_yuv2rgb(rs);
        init(ySize, uvSize, width, height);
    }

    private Allocation allocY, allocU, allocV, allocOut;

    @Override
    public void close() {
        if (allocY != null) allocY.destroy();
        if (allocU != null) allocU.destroy();
        if (allocV != null) allocV.destroy();
        if (allocOut != null) allocOut.destroy();
        bmp = null;
        allocY = null;
        allocU = null;
        allocV = null;
        allocOut = null;
        scriptC_yuv2rgb.destroy();
        scriptC_yuv2rgb = null;
        rs = null;
    }

    private void init(int ySize, int uvSize, int width, int height) {
        if (bmp == null || bmp.getWidth() != width || bmp.getHeight() != height) {
            bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            if (allocOut != null) allocOut.destroy();
            allocOut = null;
        }
        if (allocY == null || allocY.getBytesSize() != ySize) {
            if (allocY != null) allocY.destroy();
            Type.Builder yBuilder = new Type.Builder(rs, Element.U8(rs)).setX(ySize);
            allocY = Allocation.createTyped(rs, yBuilder.create(), Allocation.USAGE_SCRIPT);
        }
        if (allocU == null || allocU.getBytesSize() != uvSize || allocV == null || allocV.getBytesSize() != uvSize ) {
            if (allocU != null) allocU.destroy();
            if (allocV != null) allocV.destroy();
            Type.Builder uvBuilder = new Type.Builder(rs, Element.U8(rs)).setX(uvSize);
            allocU = Allocation.createTyped(rs, uvBuilder.create(), Allocation.USAGE_SCRIPT);
            allocV = Allocation.createTyped(rs, uvBuilder.create(), Allocation.USAGE_SCRIPT);
        }
        if (allocOut == null || allocOut.getBytesSize() != width*height*4) {
            Type rgbType = Type.createXY(rs, Element.RGBA_8888(rs), width, height);
            if (allocOut != null) allocOut.destroy();
            allocOut = Allocation.createTyped(rs, rgbType, Allocation.USAGE_SCRIPT);
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    // Enumerate valid values for this interface
    @IntDef({Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270})
    // Create an interface for validating int types
    public @interface Rotation {}

    /**
     * Converts an YUV_420 image into Bitmap.
     * @param yPlane  byte[] of Y, with pixel stride 1
     * @param uPlane  byte[] of U, with pixel stride 2
     * @param vPlane  byte[] of V, with pixel stride 2
     * @param yLine   line stride of Y
     * @param uvLine  line stride of U and V
     * @param width   width of the output image (note that it is swapped with height for portrait rotation)
     * @param height  height of the output image
     * @param rotation  rotation to apply. ROTATION_90 is for portrait back-facing camera.
     * @return RGBA_8888 Bitmap image.
     */

    public Bitmap YUV420toRGB(byte[] yPlane, byte[] uPlane, byte[] vPlane,
                              int yLine, int uvLine, int width, int height,
                              @Rotation int rotation) {
        init(yPlane.length, uPlane.length, width, height);

        allocY.copyFrom(yPlane);
        allocU.copyFrom(uPlane);
        allocV.copyFrom(vPlane);
        scriptC_yuv2rgb.set_Width(width);
        scriptC_yuv2rgb.set_Height(height);
        scriptC_yuv2rgb.set_Yline(yLine);
        scriptC_yuv2rgb.set_UVline(uvLine);
        scriptC_yuv2rgb.set_Yplane(allocY);
        scriptC_yuv2rgb.set_Uplane(allocU);
        scriptC_yuv2rgb.set_Vplane(allocV);

        switch (rotation) {
            case Surface.ROTATION_0:
                scriptC_yuv2rgb.forEach_YUV420toRGB(allocOut);
                break;
            case Surface.ROTATION_90:
                scriptC_yuv2rgb.forEach_YUV420toRGB_90(allocOut);
                break;
            case Surface.ROTATION_180:
                scriptC_yuv2rgb.forEach_YUV420toRGB_180(allocOut);
                break;
            case Surface.ROTATION_270:
                scriptC_yuv2rgb.forEach_YUV420toRGB_270(allocOut);
                break;
        }

        allocOut.copyTo(bmp);
        return bmp;
    }
}

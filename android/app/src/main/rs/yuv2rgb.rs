// from https://stackoverflow.com/questions/43642111/android-renderscript-to-convert-nv12-yuv-to-rgb

#pragma version(1)
#pragma rs java_package_name(tomerblecher.yuvtransform)
#pragma rs_fp_relaxed

rs_allocation Yplane;
uint32_t Yline;
uint32_t UVline;
rs_allocation Uplane;
rs_allocation Vplane;
rs_allocation NV21;
uint32_t Width;
uint32_t Height;

uchar4 __attribute__((kernel)) NV21toRGB(uint32_t x, uint32_t y)
{
    uchar Y = rsGetElementAt_uchar(NV21, x, y);
    uchar V = rsGetElementAt_uchar(NV21, (x & ~1), Height + (y>>1));
    uchar U = rsGetElementAt_uchar(NV21, (x & ~1)+1, Height + (y>>1));
    uchar4 rgb = rsYuvToRGBA_uchar4(Y, U, V);
    return rgb;
}

uchar4 __attribute__((kernel)) YUV420toRGB(uint32_t x, uint32_t y)
{
    uchar Y = rsGetElementAt_uchar(Yplane, x + y * Yline);
    uchar V = rsGetElementAt_uchar(Vplane, (x & ~1) + y/2 * UVline);
    uchar U = rsGetElementAt_uchar(Uplane, (x & ~1) + y/2 * UVline);
    uchar4 rgb = rsYuvToRGBA_uchar4(Y, U, V);
    return rgb;
}

uchar4 __attribute__((kernel)) YUV420toRGB_rotated(uint32_t x, uint32_t y)
{
    uchar Y = rsGetElementAt_uchar(Yplane, (Width-x-1)*Yline + y);
    uchar V = rsGetElementAt_uchar(Vplane, ((Width-x-1)/2)*UVline + (y & ~1));
    uchar U = rsGetElementAt_uchar(Uplane, ((Width-x-1)/2)*UVline + (y & ~1));
    uchar4 rgb = rsYuvToRGBA_uchar4(Y, U, V);
    return rgb;
}

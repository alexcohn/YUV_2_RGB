# YUV -> RGB Conversion in Flutter

Full working example of YUV to RGB conversion in Dart with native code(Java)

## Why is it needed?
Personally, I encountered a problem while making a [real time recognition app](https://github.com/tomerblecher/fruit-recoginition-app), using a model i trained with the [FastAI](https://www.fast.ai/) library.  
Since the library requires an RGBA image type for prediction, and the [Camera](https://pub.dev/packages/camera) plugin produces YUV images, i got stuck for a few days searching for efficient conversion solution.  
After reading a lot of half working examples of people stuck in the same situation as I, and with a lot of trial and error, I managed to get a decent solution for my purposes.

## The solution in brief
After trying a few examples, the best solution seemed to be using native code(Java) to convert the image.
A [MethodChanel](https://flutter.dev/docs/development/platform-integration/platform-channels?tab=android-channel-java-tab) is being opened upon page init, allowing a direct connection for transferring the content of the frame forwards and backwards, to and from the conversion function.

#### Average conversion time:
The conversion speed depends on the phone itself + the quality you chose for the CameraController.  
Here are the results for 3 different physical devices tested:

* Nokia 4.2 [original](https://github.com/alexcohn/YUV_2_RGB/tree/909afee5062843e18f2fb9d3ea6e5ac43ecb8fcc):
  * *medium*: 720x480 **60-90** ms yuv->rgb: **35** ms
  * *high*: 1280x720 **200** ms yuv->rgb: **63** ms

* Nokia 4.2 with [NV21toRGB (after initialization)](https://github.com/alexcohn/YUV_2_RGB/tree/8a0d7dae74356a0528604c1756131bff9f4a24a6):
  * *medium*: 720x480 **60-90** ms yuv->rgb: **18** ms
  * *high*: 1280x720 **170** ms yuv->rgb: **27** ms
  * *veryHigh*: 1920x1080 **400** ms yuv->rgb: **55** ms

* Nokia 4.2 with [YUB420toRGB (after initialization)](https://github.com/alexcohn/YUV_2_RGB/tree/b3cee7d55e1497f4fc3802caaf9479326f4632b3):
  * *medium*: 720x480 **60-90** ms yuv->rgb: **18** ms bitmap rotation: **39** ms
  * *high*: 1280x720 **170** ms yuv->rgb: **26** ms bitmap rotation: **106** ms
  * *veryHigh*: 1920x1080 **280** ms yuv->rgb: **50** ms; bitmap rotation: **166** ms; Jpeg compression **68** ms

* Nokia 4.2 with [YUB420toRGB (non-static)](https://github.com/alexcohn/YUV_2_RGB/tree/b59b62d6fd91836f43b6892bb358df282e4643db):
  * *medium*: 720x480 yuv->rgb: **9** ms; bitmap rotation: **24** ms; Jpeg compression **11** ms
  * *high*: 1280x720 yuv->rgb: **15** ms; bitmap rotation: **74** ms; Jpeg compression **26** ms
  * *veryHigh*: 1920x1080 yuv->rgb: **31** ms; bitmap rotation: **147** ms; Jpeg compression **58** ms

* Nokia 4.2 with YUB420toRGB, [with rotation](https://github.com/alexcohn/YUV_2_RGB/tree/bf1b367a239f0f91e88f0456f36780a0642a5365):
  * *medium*: 720x480 yuv->rgb: **11** ms; Jpeg compression **14** ms
  * *high*: 1280x720 yuv->rgb: **15** ms; Jpeg compression **31** ms
  * *veryHigh*: 1920x1080 yuv->rgb: **32** ms; Jpeg compression **64** ms

* Redmi Note 4:
  * *Low quality*: **~0.03-0.06** Seconds.
  * *Medium quality*: **~0.1-0.14** Seconds.
  * *High quality*: **~0.2-0.24** Seconds.

* Meizu 16:
  * *Low quality*: **~0.015-0.016** Seconds.
  * *Medium quality*: **~0.03-0.05** Seconds.
  * *High quality*: **~0.09-0.1** Seconds.
  
## What does this example includes?
* Permission handling for the camera(loop until the users accept).
* Full screen of the live camera preview
* Channeling camera frames for conversion
* Optional response stream for the RGBA Jpeg image(Uint8List)

## Important notes
* The minimum SDK version of the application is 21 ( Required by the "Camera" package).
  * **Action to preform**: Open "android/app/build.gradle" and change
   ```minSdkVersion XX``` to ```minSdkVersion 21```
* Camera privileges need to be requested at the Manifest level.
  * **Action to preform**: Open "android/app/src/main/AndroidManifest.xml",
add ```<uses-permission android:name="android.permission.CAMERA"/>``` to the ```<manifest>``` element.
  * **Note**: Do not insert the element to the ```<application>```). It should be a direct child of the manifest element.

## Camera resolution
When initializing the "CameraController" object, an enum called "ResolutionPreset" should be passed to define the camera quality.  
Those are the values for each entry as shown in the [official flutter site](https://pub.dev/documentation/camera/latest/camera/ResolutionPreset-class.html)..

* low → 352x288 on iOS, 240p (320x240) on Android
* medium → 480p (640x480 on iOS, 720x480 on Android)
* high → 720p (1280x720)
* veryHigh → 1080p (1920x1080)
* ultraHigh → 2160p (3840x2160)
* max → The highest resolution available.

To get *veryHigh* resolution or more, disable the [cap at *high* in `computeBestPreviewSize()`](https://github.com/mklim/plugins/blob/master/packages/camera/android/src/main/java/io/flutter/plugins/camera/CameraUtils.java#L28).


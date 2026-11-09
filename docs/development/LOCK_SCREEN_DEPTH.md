# Experimental Lock Screen depth

[Documentation](../README.md) · [User setup](../guide/LOCK_SCREEN.md#experimental-depth) · [Architecture](ARCHITECTURE.md)

Enable **Settings → Lock Screen → Clock → Depth effect · Experimental**, or use
the same switch in **Customize Lock Screen → Wallpaper**. The switch is off by
default and is portable in appearance backups. Lock Screen activation remains a
separate, device-local opt-in.

The first implementation separates **people** in still images. Use a portrait
with a clear silhouette and the head close to the clock; adjust the Home photo
crop when the Lock Screen follows Home. Buildings, mountains, animals, complex
hair, dark images and anime artwork may not produce a usable cutout. The normal
clock remains visible when there is no reliable person, no overlap or too much
occlusion. This is a two-layer wallpaper treatment, not a generated 3D scene.

## Processing and privacy

- A bundled DeepLab V3 model runs on the CPU through standalone LiteRT. There is
  no Google account, Play Services dependency, model download, camera access,
  analytics initialization or photo upload.
- Inference runs off the UI thread on a 257 × 257 image, with two CPU threads.
  The model and interpreter close after the single inference; the result is not
  recomputed every frame, minute or unlock. A five-second coroutine deadline
  discards late work; native inference itself is not forcibly interrupted.
- The current wallpaper's hash identifies a small private mask in Android's
  cache directory. Changing the photo or crop invalidates it. The cache holds
  only the current mask on disk; Android may clear it. A mask with no usable
  person is cached too, avoiding repeated failed inference.
- Loading/inference errors return to the normal clock. Turning the option off
  removes the foreground layer immediately. No wake lock, foreground service or
  keep-screen-on flag is added.

## Rendering and legibility

The subject reuses the background's exact crop, dim and readability gradient.
Both layers share the wallpaper's wake scale. The foreground is clipped to the
clock bounds and fades during the unlock swipe; status icons, dates,
notifications and Android authentication remain interactive and unobscured.
The foreground drawing layer does not intercept editor touches. Low-confidence
edges are feathered. The cutout must contain a
meaningful confident foreground, cover at most 24% of the clock rectangle and
less than 46% of any quarter of it. These conservative geometric checks are not
an assertion that every possible photo will look good; disabling the experiment
always restores the ordinary surface.

## Model provenance

- [Official MediaPipe image-segmentation guide](https://developers.google.com/edge/mediapipe/solutions/vision/image_segmenter)
- [TensorFlow DeepLab V3 model](https://www.kaggle.com/models/tensorflow/deeplabv3)
- Bundled source: `https://storage.googleapis.com/mediapipe-models/image_segmenter/deeplab_v3/float32/latest/deeplab_v3.tflite`
- Source file: `app/src/main/assets/depth/deeplab_v3.tflite`, 2,780,176 bytes.
- SHA-256: `ff36e24d40547fe9e645e2f4e8745d1876d6e38b332d39a82f0bf0f5d1d561b3`.
- TensorFlow / MediaPipe model and LiteRT runtime: Apache 2.0; see the bundled
  `assets/licenses/Apache-2.0.txt` and `NOTICE.txt`.
- Input: RGB float32, `[1,257,257,3]`, `(channel / 127.5) - 1`.
- Output: `[1,257,257,21]` logits; stable softmax gives the PASCAL VOC person
  class (15). The accompanying label map is part of the model file.

The small instrumentation-only `app/src/androidTest/assets/depth-person.jpg`
comes from MediaPipe's public test asset
`https://storage.googleapis.com/mediapipe-assets/segmentation_input_rotation0.jpg`.
Fixture SHA-256: `5bf58d8af1f1c33224f3f3bc0ce451c8daf0739cc15a86d59d8c3bf2879afb97`.
It verifies actual CPU inference and cache reuse on the AOSP emulator; it is not
included in the published app. Synthetic mask tests cover crop alignment, single
application of dim and safe fallback on blank backgrounds.

Design reference: [Apple Lock Screen customization](https://support.apple.com/en-il/guide/iphone/iph4d0e6c351/ios).
The experiment uses Android rendering and does not access Apple's implementation.

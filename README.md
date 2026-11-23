# HTML2MP4 Renderer (Android 15 · Exynos 850)

Native Kotlin + Jetpack Compose application to render HTML5 (DOM, CSS, Canvas, WebGL, Audio/Video, Web Audio API) into hardware-accelerated MP4 on **Exynos 850 (ARM64-v8a)** running **Android 15 (API 35)**.

## Architecture Highlights
- **Zero-Copy Hardware Pipeline**: `WebView` -> `HardwareRenderer` (`RenderNode`) -> `MediaCodec.createInputSurface()` -> `MediaMuxer`. Video frames stay in Mali-G52 GPU/MFC hardware buffers without touching JVM heap.
- **Audio Capture Bridge**: Injects a Web Audio API + `HTMLMediaElement` tap to capture synchronized PCM audio and encode via `MediaCodec` AAC-LC.
- **Android 15 FGS Compliance**: Uses `FOREGROUND_SERVICE_MEDIA_PROCESSING` with `Service.onTimeout(startId, fgsType)` graceful shutdown handling.
- **Strict Memory Safety**: Streams SAF HTML files via `WebResourceResponse` without loading HTML strings into UI state, plus `ComponentCallbacks2` trim handling.

## GitHub Actions CI
Push to `main` or trigger `workflow_dispatch` to build `HTML2MP4-Renderer-v1.0.0-arm64-v8a-debug.apk` and publish it to Actions Artifacts & GitHub Releases.

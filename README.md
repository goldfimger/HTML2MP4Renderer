# HTML2MP4 Renderer (Android 15 · Multi-Chipset, Tablet & Chromebook)

Native Kotlin + Jetpack Compose application to render HTML5 (DOM, CSS, Canvas, WebGL, Audio/Video, Web Audio API) into hardware-accelerated MP4 completely offline.

Built by **Goldfimger** · Repo: https://github.com/goldfimger/HTML2MP4Renderer

## Architecture & Device Support
- **Multi-Chipset Hardware Pipeline**: Auto-tunes `MediaCodec` encoder alignment and memory budgets for **Samsung Exynos (850/1xxx/2xxx)**, **Qualcomm Snapdragon**, **MediaTek Dimensity/Helio**, **Google Tensor**, and **Intel/AMD ChromeOS (Googlebook)**.
- **Zero-Copy Hardware Surface**: `WebView` -> `VirtualDisplay` / `Presentation` -> `MediaCodec.createInputSurface()` -> `MediaMuxer`.
- **Responsive Phone / Tablet / Chromebook UI**: Uses Material You (Monet) with adaptive `NavigationBar` on phones and `NavigationRail` + 2-column workspace on tablets & ChromeOS laptops.
- **Android 15 FGS Compliance**: Uses `FOREGROUND_SERVICE_MEDIA_PROCESSING` with `Service.onTimeout(startId, fgsType)`.
- **Material You Dynamic Icon**: Golden-Ratio symmetrical `html` / `mp4` + smile-curved arrow icon powered by `@android:color/system_accent1_*` and Android 13–15 `monochrome` themed icon support.

## GitHub Actions CI
Push to `main` or trigger `workflow_dispatch` to build `HTML2MP4-Renderer-v1.1.0-universal-debug.apk` and publish it to Actions Artifacts & GitHub Releases.

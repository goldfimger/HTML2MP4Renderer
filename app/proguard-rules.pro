# Preserve @JavascriptInterface methods on HtmlBridge
-keepclassmembers class com.html2mp4.renderer.engine.HtmlBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-keepattributes *Annotation*

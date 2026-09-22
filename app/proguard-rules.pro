# PSBDx DevBrowser - ProGuard / R8 rules
# Copyright (C) 2024 PSBDx DevBrowser Contributors - GPLv3

# Keep WebView JavaScript interface methods (annotated with @JavascriptInterface)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Room entities and DAOs
-keep class com.devbrowser.psbdx.data.** { *; }

# Keep Kotlin coroutines internals
-dontwarn kotlinx.coroutines.**

# Keep Compose runtime
-dontwarn androidx.compose.**

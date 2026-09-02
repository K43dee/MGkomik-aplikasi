# Keep JS interface methods used from WebView
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

# Al-Mahalla Optimized Proguard & R8 Rules
# Keep application activities, services, receivers, and application class
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep all AlMahala app classes, native bridges and models
-keep class com.almahala.netplay.** { *; }
-keepclassmembers class com.almahala.netplay.** { *; }

# Keep Serializable and Parcelable models
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# JNI & Native
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep attributes and annotations for JNI and WebView reflection
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,JavascriptInterface

# Zego Express Video & Audio Calling Engine (Full SDK protection for JNI)
-keep class im.zego.** { *; }
-keepclassmembers class im.zego.** { *; }
-dontwarn im.zego.**

# Android WebView & JavaScript Interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class android.webkit.** { *; }
-dontwarn android.webkit.**
-keepclassmembers class * extends android.webkit.WebView { public *; }
-keepclassmembers class * extends android.webkit.WebViewClient { public *; }
-keepclassmembers class * extends android.webkit.WebChromeClient { public *; }

# OkHttp & Coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn kotlinx.coroutines.**

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# Jetpack Compose & Material
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }



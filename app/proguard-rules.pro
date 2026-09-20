# Gson reflects over the Retrofit response DTOs and Jam protocol messages in this package.
-keep class com.auralis.app.network.** { *; }
-keep class com.auralis.app.data.remote.** { *; }
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Retrofit under R8 full mode
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# WebRTC calls into Java from native code by name, so nothing in org.webrtc may be renamed or
# stripped. R8 cannot see those references, and the failure mode is a call that builds fine and
# then crashes on the first frame.
-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }
-dontwarn org.webrtc.**

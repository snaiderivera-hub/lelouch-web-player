# Reglas R8 / ProGuard específicas para Lelouch Android
# Nota: Ya no se mantiene com.lelouch.** en claro para permitir ofuscación y reducción R8.

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Jetpack Compose + Lifecycle 2.8.x interop (LocalLifecycleOwner se resuelve por reflexión en Release)
-keep class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt { *; }
-keep class androidx.lifecycle.compose.LocalLifecycleOwnerKt { *; }

# Kotlinx Serialization
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep,allowobfuscation,allowshrinking class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers,allowobfuscation class *$$serializer {
    *;
}
-keepclassmembers,allowobfuscation class *$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Retrofit & API Xtream (mantiene nombres/anotaciones de endpoints @GET/@Query)
-keep interface com.lelouch.core.network.XtreamApiService { *; }
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-dontwarn kotlin.Unit
-dontwarn retrofit2.Platform$Java8
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Room Database (LelouchDatabase_Impl resuelto por reflexión en tiempo de ejecución)
-keep class * extends androidx.room.RoomDatabase
-keep class com.lelouch.core.database.LelouchDatabase_Impl { *; }
-dontwarn androidx.room.paging.**

# Parcelable CREATOR
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# LibVLC JNI & Event callbacks
-keep class org.videolan.libvlc.** { *; }
-dontwarn org.videolan.libvlc.**

# ExcavPlayer Proguard Rules (R8 Full Mode Optimized)

# General keep attributes
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# -----------------------------------------------------------------------------
# Kotlin Serialization & Models
# -----------------------------------------------------------------------------
-keepattributes *Annotation*,Signature
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.json.** { *; }
-keep class com.excavplayer.domain.model.** { *; }
-keep class com.excavplayer.data.database.entity.** { *; }
-keep class com.excavplayer.data.database.dao.** { *; }
-keep class com.excavplayer.update.GitHubRelease { *; }
-keep class com.excavplayer.update.GitHubAsset { *; }

# -----------------------------------------------------------------------------
# Dagger / Hilt
# -----------------------------------------------------------------------------
-dontwarn javax.annotation.**
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class * extends androidx.hilt.work.HiltWorker { *; }
-keepclassmembers,allowobfuscation class * {
    @javax.inject.* *;
    @dagger.* *;
}

# -----------------------------------------------------------------------------
# AndroidX Media3 / ExoPlayer
# -----------------------------------------------------------------------------
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.decoder.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keepclassmembers class androidx.media3.** {
    public <init>(...);
}

# -----------------------------------------------------------------------------
# AndroidX Room
# -----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class androidx.room.paging.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# -----------------------------------------------------------------------------
# AndroidX Paging
# -----------------------------------------------------------------------------
-keep class androidx.paging.** { *; }
-dontwarn androidx.paging.**

# -----------------------------------------------------------------------------
# AndroidX WorkManager
# -----------------------------------------------------------------------------
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# -----------------------------------------------------------------------------
# Jetpack Compose & Haze
# -----------------------------------------------------------------------------
-keepclassmembers class androidx.compose.** { *; }
-keep class dev.chrisbanes.haze.** { *; }
-dontwarn dev.chrisbanes.haze.**

# -----------------------------------------------------------------------------
# Coroutines & Logging
# -----------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn sun.misc.Unsafe
-dontwarn java.lang.invoke.**

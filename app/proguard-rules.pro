# Add project specific ProGuard rules here.

# Keep data models
-keep class com.sameerasw.draft.data.model.** { *; }
-keepclassmembers class com.sameerasw.draft.data.model.** { *; }

# Keep ViewModel constructors
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# JGit rules
-dontwarn org.eclipse.jgit.**
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }

# Markwon rules
-dontwarn io.noties.markwon.**
-keep class io.noties.markwon.** { *; }

# Crypto Tink & annotation suppressions
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }

# Room — keep generated _Impl classes so Room can instantiate them via reflection
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Database class * { *; }
-keepclassmembers @androidx.room.Database class * { *; }
-dontwarn androidx.room.**

# WorkManager — keep impl and initializer classes
-keep class androidx.work.** { *; }
-keep class androidx.work.impl.** { *; }
-keepclassmembers class androidx.work.impl.** { *; }
-dontwarn androidx.work.**

# AndroidX Startup — keep initializers
-keep class androidx.startup.** { *; }
-keep class * extends androidx.startup.Initializer { *; }
-keepclassmembers class * extends androidx.startup.Initializer { *; }

# Ensure @Keep annotations are always honored
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

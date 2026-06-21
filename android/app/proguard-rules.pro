# React Native
-keep class com.facebook.react.** { *; }
-keep class com.facebook.hermes.** { *; }
-keep class com.facebook.jni.** { *; }
-dontwarn com.facebook.react.**
-dontwarn com.facebook.hermes.**

# Reanimated
-keep class com.swmansion.reanimated.** { *; }
-keep class com.facebook.react.turbomodule.** { *; }

# Expo modules
-keep class expo.modules.** { *; }
-dontwarn expo.modules.**

# Glance widget
-keep class androidx.glance.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**

# Keep JS interface bridges
-keepclassmembers class * {
    @com.facebook.react.bridge.ReactMethod *;
}
-keepclassmembers class * {
    @com.facebook.proguard.annotations.DoNotStrip *;
}
-keepclassmembers class * {
    @com.facebook.proguard.annotations.KeepGettersAndSetters *;
}

# Keep native methods
-keepclassmembers class * {
    native <methods>;
}

# Preserve stack trace line numbers
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

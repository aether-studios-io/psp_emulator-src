# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep line numbers so crash stack traces can be deobfuscated with mapping.txt,
# but hide the original source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================
# kotlinx.serialization
# The Retrofit converter resolves serializers reflectively via serializer(type);
# R8 full mode can strip the generated Companion/serializer members it looks up.
# Official rules from the kotlinx.serialization README.
# ============================================================
# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `Companion.serializer(...)`.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# ============================================================
# Google Mobile Ads SDK (play-services-ads 23.6.0)
# ============================================================
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# ============================================================
# UMP Consent SDK (user-messaging-platform 3.1.0)
# ============================================================
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**
# ============================================================
# RetroNova: LibretroDroid (GPL-3.0). Its native layer calls back into these classes by
# name over JNI (GLRetroView callbacks, Variable, VirtualFile...), so R8 must not rename
# or strip them.
# ============================================================
-keep class com.swordfish.libretrodroid.** { *; }
-keep class com.swordfish.radialgamepad.** { *; }
-dontwarn com.swordfish.**

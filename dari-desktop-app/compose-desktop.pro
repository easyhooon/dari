# Release (ProGuard) build rules, on top of Compose's defaults.

# Swing Main dispatcher is found through META-INF/services, which ProGuard cannot trace.
-keep class kotlinx.coroutines.swing.SwingDispatcherFactory { *; }
-keep class * implements kotlinx.coroutines.internal.MainDispatcherFactory { *; }

# kotlinx.serialization: keep generated serializers of the wire protocol.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.easyhooon.dari.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.easyhooon.dari.**$$serializer { *; }
-keepclassmembers class com.easyhooon.dari.** {
    *** Companion;
}

# Sentry (crash reports) loads options, integrations and serializers reflectively; optional
# integrations reference libraries Dari does not ship.
-keep class io.sentry.** { *; }
-dontwarn io.sentry.**

# JNA binds the Windows DWM call (dark title bar) through a proxy of this interface and its own reflection.
-keep class com.sun.jna.** { *; }
-keep interface com.easyhooon.dari.desktop.app.WindowsChrome$* { *; }
-dontwarn com.sun.jna.**

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

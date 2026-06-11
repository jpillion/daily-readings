# Daily Reading Planner — R8 keep rules (S9-T5).
#
# Compose, Hilt, Room, Glance, DataStore and androidx.browser all ship consumer keep
# rules in their AARs; nothing app-side is needed for them. The two app-specific concerns:

# 1) kotlinx.serialization: the runtime ships consumer rules for serializer lookup, but we
#    keep the @Serializable plan DTOs' generated serializers explicitly as defense in depth —
#    a silently stripped serializer would break plan loading only at runtime.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers @kotlinx.serialization.Serializable class com.jpillion.dailyreadingplanner.** {
    static **$Companion Companion;
}
-keepclasseswithmembers class com.jpillion.dailyreadingplanner.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# 2) Debug-only surface: nothing to keep — StrictMode setup in DailyReadingsApp is guarded
#    by BuildConfig.DEBUG (a compile-time constant in release), so R8 strips it entirely.

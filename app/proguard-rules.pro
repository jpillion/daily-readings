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

# 3) WorkManager reflection (Glance renders widgets via a WorkManager session):
#    WorkerWrapper instantiates InputMerger subclasses reflectively by class name; R8
#    strips OverwritingInputMerger's zero-arg constructor without this, killing the
#    Glance session worker before any RemoteViews are delivered — the widget then hangs
#    forever on the launcher's loading spinner (observed on device, 1.0.0/1.1.0).
-keep class * extends androidx.work.InputMerger {
    <init>();
}

# 4) V3 read-only bible.db (Room): Room ships consumer keep rules for entities/DAOs it
#    generates against, so this is defense-in-depth — keep the verse entity's members so a
#    column-mapping field is never stripped (a stripped field would fail the asset read only
#    at runtime, after the createFromAsset copy). Mirrors the serialization keep above.
-keepclassmembers class com.jpillion.dailyreadingplanner.bible.data.VerseEntity {
    <fields>;
    <init>(...);
}

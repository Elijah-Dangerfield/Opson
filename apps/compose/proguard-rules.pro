# R8 rules for the release build.
#
# Minification is on (see ApplicationConventionPlugin). Play flags an app under
# 25% obfuscation as "below our threshold" with a Feb 2027 deadline, and the
# shrinking and startup wins come with it.
#
# The rule of thumb: R8 breaks whatever is found by *name at runtime* rather
# than referenced in code. In a KMP app of this shape that is always the same
# three things — serialization, type-safe navigation routes, and the DI graph's
# generated entry point. Every rule below is here for a reason that is written
# down; if one looks redundant, read the reason before deleting it.

# ---------------------------------------------------------------------------
# kotlinx.serialization
# ---------------------------------------------------------------------------
# Serializers are resolved through a generated `Companion.serializer()` that
# nothing calls directly, so R8 sees them as dead. Losing one does not fail the
# build; it throws at runtime the first time that model crosses the wire.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.opson.**$$serializer { *; }
-keepclassmembers class com.opson.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Enum constants are matched by name during deserialization.
-keepclassmembers enum com.opson.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Every @Serializable model, with its fields. Names in the JSON are the field
# names unless @SerialName says otherwise, so renaming silently changes the
# wire format — which the server would reject and no compile step would catch.
-keep @kotlinx.serialization.Serializable class com.opson.** { *; }

# ---------------------------------------------------------------------------
# Type-safe navigation
# ---------------------------------------------------------------------------
# Routes are @Serializable classes that androidx.navigation resolves by type.
# Covered by the rule above, kept explicitly because this is the failure that
# would be hardest to attribute: navigation stops working with an *argument*
# error rather than a missing-class error.
-keep class com.opson.libraries.navigation.** { *; }
-keep class * extends com.opson.libraries.navigation.Route { *; }

# ---------------------------------------------------------------------------
# DI (kotlin-inject-anvil)
# ---------------------------------------------------------------------------
# The graph is generated at compile time, so it needs no reflection help. The
# generated component is referenced through `::class.create`, which R8 can
# follow — but the entry point is worth pinning so a mistake here is loud.
-keep class com.opson.**AppComponent* { *; }

# ---------------------------------------------------------------------------
# Room type converters
# ---------------------------------------------------------------------------
# Room matches a `.addTypeConverter(instance)` against the class identities its
# codegen baked into `getRequiredTypeConverters()`. R8's horizontal class
# merging folds a small converter class into an unrelated one — a converter got
# merged into `io.sentry.hints.i` here — and Room then rejects it while building
# the database:
#
#   IllegalArgumentException: Unexpected type converter io.sentry.hints.i@...
#   Annotate TypeConverter class with @ProvidedTypeConverter annotation or
#   remove this converter from the builder
#
# The message points at the annotation, which is already present; the real
# cause is the rename. This crashes in Application.onCreate, so the app dies
# before its first frame — and only in a minified build.
-keep @androidx.room.ProvidedTypeConverter class * { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter <methods>;
}

# ---------------------------------------------------------------------------
# Wiretap — present in debug, swapped for a no-op in release
# ---------------------------------------------------------------------------
# `releaseImplementation(wiretap.ktor.noop)` covers the Ktor plugins but not the
# console launcher, so `launchNetworkInspector()` compiles against a class that
# is not in a release APK. Unminified nobody notices: the call is unreachable
# because installation and launch are both gated on `BuildInfo.isDebug`. R8 is
# right to flag it and this says "yes, on purpose".
-dontwarn dev.skymansandy.wiretap.**

# ---------------------------------------------------------------------------
# Readable crash output
# ---------------------------------------------------------------------------
# Keep exception *class names*. Obfuscated, every Sentry issue and every ANR
# report arrives titled `a.b.c` and has to be un-mangled before it can even be
# triaged. Throwable names are a rounding error in APK size and the difference
# between a readable inbox and an unreadable one.
-keepnames class * extends java.lang.Throwable

# Line numbers in stack traces, and a single fake source-file name so the
# mapping stays the only thing needed to read them back.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Logging is deliberately NOT stripped. The default `proguard-android-optimize`
# file does not remove `android.util.Log` calls, and nothing here adds an
# `-assumenosideeffects` rule to do it. The app logs through :libraries:telemetry,
# which feeds the telemetry pipe — stripping it would blind production, not just
# logcat. Do not add a log-stripping rule without checking where logs go first.

# ---------------------------------------------------------------------------
# Third parties with JVM-only branches that Android never reaches
# ---------------------------------------------------------------------------
# Ktor, Supabase, Sentry and Room ship their own consumer rules. These cover the
# warnings left over from code paths that only exist on a desktop JVM.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
-dontwarn kotlinx.coroutines.debug.**

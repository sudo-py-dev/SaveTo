# SaveTo ProGuard/R8 Rules

# Keep the share activity and viewmodel entry points
-keep class save.to.com.ShareActivity { *; }
-keep class save.to.com.SaveViewModel { *; }

# Keep ActivityResult contracts used via reflection
-keep class save.to.com.ShareActivity$CreateDocumentContract { *; }

# AndroidX Activity Result API
-keep class androidx.activity.result.** { *; }
-keep class androidx.activity.result.contract.** { *; }

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }

# Remove verbose kotlin metadata
-dontwarn kotlin.reflect.jvm.internal.**
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkNotNullParameter(...);
    static void checkNotNullExpressionValue(...);
    static void checkParameterIsNotNull(...);
    static void checkExpressionValueIsNotNull(...);
}

# Strip debug info for smaller binary
-allowaccessmodification
-repackageclasses ''
-optimizationpasses 5

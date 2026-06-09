-keep class save.to.com.SaveViewModel {
    public <init>(android.app.Application);
}

-repackageclasses 's'
-allowaccessmodification

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkNotNull(...);
    static void checkNotNullParameter(...);
    static void checkNotNullExpressionValue(...);
    static void checkParameterIsNotNull(...);
    static void checkExpressionValueIsNotNull(...);
    static void throwParameterIsNullException(...);
    static void throwNpe(...);
}

-keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable,Signature,InnerClasses,EnclosingMethod

-dontwarn kotlinx.coroutines.**
-dontwarn kotlin.reflect.jvm.internal.**

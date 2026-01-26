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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ===== Forge 框架混淆规则 =====

# 保持 Forge 核心类
-keep class com.hrm.forge.Forge {
    public *;
}

-keep class com.hrm.forge.loader.ForgeApplication {
    public *;
    protected *;
}

# 保持 Instrumentation 相关类（关键，不能混淆）
-keep class com.hrm.forge.loader.instrumentation.InstrumentationProxy {
    public *;
    # 这两个方法特别重要，不能被混淆
    public ** execStartActivity(...);
    public ** newActivity(...);
}

-keep class com.hrm.forge.loader.instrumentation.HookHelper {
    public *;
}

-keep class com.hrm.forge.loader.instrumentation.StubActivity* {
    *;
}

-keep class com.hrm.forge.loader.instrumentation.ActivityInfoManager {
    public *;
}

-keep class com.hrm.forge.loader.instrumentation.StubService {
    *;
}

-keep class com.hrm.forge.loader.instrumentation.ServiceHelper {
    public *;
}

-keep class com.hrm.forge.loader.instrumentation.AMSHookHelper {
    public *;
}

-keep class com.hrm.forge.loader.instrumentation.AMSHookHelper$AMSInvocationHandler {
    *;
}

# 保持 Builder Service
-keep class com.hrm.forge.builder.ForgeBuilderService {
    public *;
}

-keep class com.hrm.forge.builder.ForgeBuilderService$VersionInfo {
    *;
}

# 保持 Logger 接口
-keep interface com.hrm.forge.logger.ILogger {
    *;
}

# 保持反射使用的类
-keepclassmembers class * {
    public <init>(android.content.Context);
}

# 保持 ApplicationLike 的生命周期方法
-keepclassmembers class * {
    public void attachBaseContext(android.content.Context);
    public void onCreate();
    public void onTerminate();
    public void onLowMemory();
    public void onTrimMemory(int);
}

# 保持 Kotlin 相关
-keepattributes *Annotation*
-keepclassmembers class kotlin.Metadata {
    *;
}

# 保持协程
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

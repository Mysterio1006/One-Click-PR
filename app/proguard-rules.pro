# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# -------------------------------------------------------------------
# AndroidX Security / Google Tink 混淆规则
# -------------------------------------------------------------------
# 忽略编译期错误注解，防止 R8 报错 Missing class com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.**

# 忽略 Tink 的其它警告
-dontwarn com.google.crypto.tink.**

# -------------------------------------------------------------------
# OkHttp3 / Okio 混淆规则 (避免网络请求库相关的偶尔缺失警告)
# -------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**

# -------------------------------------------------------------------
# Gson 混淆规则
# -------------------------------------------------------------------
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# 如果你使用了 WebView 并带有 JS, 取消以下注释
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# 保留调试时的行号信息，方便线上 Crash 定位
-keepattributes SourceFile,LineNumberTable

# 隐藏源代码的实际类名，提升一定的反编译难度
#-renamesourcefileattribute SourceFile
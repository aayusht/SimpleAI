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

# Prevent LiteRT-LM internal JNI classes from being renamed
-keep class com.google.ai.edge.litertlm.** { *; }

# Keep all classes and methods that use the Tool annotations
-keepclassmembers class * {
    @com.google.ai.edge.litertlm.Tool <methods>;
    @com.google.ai.edge.litertlm.ToolParam <fields>;
}

# Keep the callback interface used by nativeSendMessageAsync
-keep interface com.google.ai.edge.litertlm.MessageCallback { *; }
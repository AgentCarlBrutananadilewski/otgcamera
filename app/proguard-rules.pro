# UVCCamera native library
-keep class org.uvccamera.** { *; }
-dontwarn org.uvccamera.**

# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class hilt_aggregated_deps.** { *; }

# Compose
-keepclassmembers,allowobfuscation interface androidx.compose.ui.tooling.preview.PreviewParameter$Provider {
    public <methods>;
}

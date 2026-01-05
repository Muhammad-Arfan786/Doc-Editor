# Add project specific ProGuard rules here.

# Keep Apache POI classes
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**

# Keep PDF Viewer
-keep class com.github.barteksc.** { *; }

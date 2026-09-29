# Keep real class names so on-screen error details stay readable. Shrinking still applies.
-dontobfuscate

# youtubedl-android + FFmpeg native libs
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**
# commons-compress (used to unpack Python/FFmpeg) registers zip extra fields via reflection.
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.**
# Jackson parses yt-dlp's --dump-json and the updater's GitHub JSON via reflection.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**
-dontwarn java.beans.**
-dontwarn org.w3c.dom.bootstrap.**
# mp3agic
-keep class com.mpatric.mp3agic.** { *; }
# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

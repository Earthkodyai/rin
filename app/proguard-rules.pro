# RinAlarm release rules (6.2). Most libraries (Hilt, Room, kotlinx.serialization, Compose) ship their own.

# Vosk (Repeat after Rin, task 3.5) calls its native library through JNA, which finds classes and fields by reflection.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**

# 6.1 security review: debug and verbose logs (words the speech game heard, line picks) never reach a release build.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

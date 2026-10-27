# CountdownDay R8 规则
# Room 的 AAR 自带 consumer rules，Compose 无需额外规则；此处只补充通用保护。

-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# 保留 Room 生成的实现类与数据库实体
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }

# 协程内部类通过反射访问，避免误删
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# 防止 R8 对 org.json 的 warn（Android 平台自带）
-dontwarn org.json.**

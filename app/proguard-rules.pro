# Shizuku：AIDL 生成类、Provider 与远程进程接口通过系统调用/反射使用，整包保留。
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.server.** { *; }
-keep interface moe.shizuku.server.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# AIDL/Parcelable 传输依赖 CREATOR。
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Hilt / Room / Compose / WorkManager / DataStore 通过 consumer rules 自带保留规则，
# 这里保留应用入口组件，便于系统与 Hilt 反射查找。
-keep class com.t2m.monitor.MonitorApp { *; }
-keep class com.t2m.monitor.ui.MainActivity { *; }
-keep class com.t2m.monitor.service.MonitorService { *; }

# 保留注解信息（Room/WorkManager 运行时读取）。
-keepattributes *Annotation*

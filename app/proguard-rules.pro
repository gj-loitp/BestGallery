-keep class com.eagle.** { *; }
-dontwarn android.graphics.Canvas
-dontwarn com.eagle.**
-dontwarn org.apache.**

# Picasso
-dontwarn com.squareup.picasso**
-keep class com.squareup.picasso.**{*;}
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn okhttp3.internal.platform.ConscryptPlatform

-keep class com.wang.avi.** { *; }
-keep class com.wang.avi.indicators.** { *; }

-dontwarn com.squareup.okhttp.**


# for event bug
-keepattributes *Annotation*
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

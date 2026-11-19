# R8 / resource shrinking is on for release. SQLCipher's AAR already ships
# consumer rules that keep net.zetetic.database.** and its JNI methods.
# The rules below cover this app's manifest entry points and stack traces.

-keep class my.toolmentum.hundredlife.MainActivity { <init>(); }
-keep class my.toolmentum.hundredlife.ReminderReceiver { <init>(); }

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room creates its generated database classes (e.g. androidx.work.impl.WorkDatabase_Impl) by
# reflection. WorkManager 2.7 / Room 2.2.5 come in through the Google Mobile Ads SDK and ship
# no rules for R8 full mode, so without this the release build crashes at startup with
# "Failed to create an instance of androidx.work.impl.WorkDatabase".
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Daily reminders: WorkManager creates the worker by reflection from its class name.
-keep class com.nexusplaylabss.freecell.reminders.ReminderWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }

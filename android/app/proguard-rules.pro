# R8 runs in full mode (AGP 9). In full mode "-keep class X" no longer keeps the
# default constructor, so classes created by reflection need it kept explicitly.

# Room (pulled in by WorkManager via play-services-ads) creates the DB with
# Class.forName("..._Impl").newInstance(). Old room-runtime 2.2.5 rules keep only the name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# androidx.startup creates initializers (WorkManagerInitializer etc.) by reflection.
-keep class * extends androidx.startup.Initializer { <init>(); }

# WorkManager creates workers and input mergers by reflection.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class * extends androidx.work.InputMerger { <init>(); }

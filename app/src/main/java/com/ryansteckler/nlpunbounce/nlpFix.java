package com.ryansteckler.nlpunbounce;

/**
 * Created by ryan steckler on 8/18/14.
 */
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import java.util.ArrayList;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

import de.robv.android.xposed.XC_MethodHook;

public class nlpFix implements IXposedHookLoadPackage {

    private static final String TAG = "NlpUnbounce: ";
    private static final String VERSION = "1.1.3"; //This needs to be pulled from the manifest or gradle build.
    private long mLastLocatorAlarm = 0;  // Last alarm attempt
    private long mLastDetectionAlarm = 0;  // Last alarm attempt
    private long mLastNlpWakeLock = 0;  // Last wakelock attempt
    private long mLastNlpCollectorWakeLock = 0;  // Last wakelock attempt

    private static boolean showedUnsupportedAlarmMessage = false;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {

        if (lpparam.packageName.equals("android")) {

            XposedBridge.log(TAG + "Version " + VERSION);

            final XSharedPreferences prefs = new XSharedPreferences(nlpFix.class.getPackage().getName());
            prefs.reload();

            hookAlarms(lpparam, prefs);
            hookWakeLocks(lpparam, prefs);

        }
    }

    private void hookAlarms(LoadPackageParam lpparam, XSharedPreferences prefs) {
        boolean alarmsHooked = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
        {
            //Try for alarm hooks for API levels >= 19
            debugLog(prefs, "Attempting 19to20 AlarmHook");
            try19To20AlarmHook(lpparam, prefs);
            debugLog(prefs, "Successful 19to20 AlarmHook");
            alarmsHooked = true;
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1 &&
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2)
        {
            //Try for alarm hooks for API levels 15-18.
            debugLog(prefs, "Attempting 15to18 AlarmHook");
            try15To18AlarmHook(lpparam, prefs);
            debugLog(prefs, "Successful 15to18 AlarmHook");
            alarmsHooked = true;

        }

        if (!alarmsHooked) {
            XposedBridge.log(TAG + "Unsupported Android version trying to hook Alarms.");
        }
    }

    private void hookWakeLocks(LoadPackageParam lpparam, XSharedPreferences prefs) {
        boolean wakeLocksHooked = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            //Try for wakelock hooks for API levels 19-20
            debugLog(prefs, "Attempting 19to20 WakeLockHook");
            try19To20WakeLockHook(lpparam, prefs);
            debugLog(prefs, "Successful 19to20 WakeLockHook");
            wakeLocksHooked = true;
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 &&
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            //Try for wakelock hooks for API levels 17-18
            debugLog(prefs, "Attempting 17to18 WakeLockHook");
            try17To18WakeLockHook(lpparam, prefs);
            debugLog(prefs, "Successful 17to18 WakeLockHook");
            wakeLocksHooked = true;
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1 &&
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN)
        {
            //Try for wakelock hooks for API levels 15-16
            debugLog(prefs, "Attempting 15to16 WakeLockHook");
            try15To16WakeLockHook(lpparam, prefs);
            debugLog(prefs, "Successful 15to16 WakeLockHook");
            wakeLocksHooked = true;
        }

        if (!wakeLocksHooked) {
            XposedBridge.log(TAG + "Unsupported Android version trying to hook WakeLocks.");
        }
    }

    private void try19To20WakeLockHook(final LoadPackageParam lpparam, final XSharedPreferences prefs) {
        findAndHookMethod("com.android.server.power.PowerManagerService", lpparam.classLoader, "acquireWakeLockInternal", android.os.IBinder.class, int.class, String.class, String.class, android.os.WorkSource.class, int.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

//                debugLog(prefs, "Wakelock params: " + param.args[0] + ", " + param.args[1] + ", " + param.args[2] + ", " + param.args[3] + ", " + param.args[4] + ", " + param.args[5] + ", " + param.args[6]);
                String wakeLockName = (String)param.args[2];
                handleWakeLock(param, prefs, wakeLockName);
            }
        });
    }

    private void try17To18WakeLockHook(LoadPackageParam lpparam, final XSharedPreferences prefs) {
        findAndHookMethod("com.android.server.power.PowerManagerService", lpparam.classLoader, "acquireWakeLockInternal", android.os.IBinder.class, int.class, String.class, android.os.WorkSource.class, int.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String wakeLockName = (String)param.args[2];
                handleWakeLock(param, prefs, wakeLockName);
            }
        });
    }

    private void try15To16WakeLockHook(LoadPackageParam lpparam, final XSharedPreferences prefs) {
        findAndHookMethod("com.android.server.PowerManagerService", lpparam.classLoader, "acquireWakeLockLocked", int.class, android.os.IBinder.class, int.class, int.class, String.class, android.os.WorkSource.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String wakeLockName = (String)param.args[4];
                handleWakeLock(param, prefs, wakeLockName);
            }
        });
    }

    private void try19To20AlarmHook(LoadPackageParam lpparam, final XSharedPreferences prefs) {
        findAndHookMethod("com.android.server.AlarmManagerService", lpparam.classLoader, "triggerAlarmsLocked", ArrayList.class, long.class, long.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                ArrayList<Object> triggers = (ArrayList<Object>)param.args[0];
                handleAlarm(prefs, param, triggers);
            }
        });
    }

    private void try15To18AlarmHook(LoadPackageParam lpparam, final XSharedPreferences prefs) {
        findAndHookMethod("com.android.server.AlarmManagerService", lpparam.classLoader, "triggerAlarmsLocked", ArrayList.class, ArrayList.class, long.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                ArrayList<Object> triggers = (ArrayList<Object>)param.args[1];
                handleAlarm(prefs, param, triggers);
            }
        });
    }

    private void handleWakeLock(XC_MethodHook.MethodHookParam param, XSharedPreferences prefs, String wakeLockName) {
        prefs.reload();


        if (wakeLockName.equals("NlpCollectorWakeLock"))
        {
            //If we're blocking them
            if (prefs.getBoolean("wakelock_collector_enabled", true)) {
                int collectorMaxFreq = tryParseInt(prefs.getString("wakelock_collector_seconds", "240"));
                collectorMaxFreq *= 1000; //convert to ms

                //Debounce this to our minimum interval.
                final long now = SystemClock.elapsedRealtime();
                long timeSinceLastWakeLock = now - mLastNlpCollectorWakeLock;

                if (timeSinceLastWakeLock < collectorMaxFreq) {
                    //Not enough time has passed since the last wakelock.  Deny the wakelock
                    param.setResult(null);
                    incrementBlockCount(prefs, param, "NlpCollectorWakeLock");

                    debugLog(prefs, "Preventing NlpCollectorWakeLock.  Max Interval: " + collectorMaxFreq + " Time since last granted: " + timeSinceLastWakeLock);

                } else {
                    //Allow the wakelock
                    XposedBridge.log(TAG + "Allowing NlpCollectorWakeLock.  Max Interval: " + collectorMaxFreq + " Time since last granted: " + timeSinceLastWakeLock);
                    mLastNlpCollectorWakeLock = now;
                }
            }
        }
        else if (wakeLockName.equals("NlpWakeLock"))
        {
            if (prefs.getBoolean("wakelock_nlp_enabled", true)) {

                int nlpMaxFreq = tryParseInt(prefs.getString("wakelock_nlp_seconds", "240"));
                nlpMaxFreq *= 1000; //convert to ms

                //Debounce this to our minimum interval.
                final long now = SystemClock.elapsedRealtime();
                long timeSinceLastWakeLock = now - mLastNlpWakeLock;

                if (timeSinceLastWakeLock < nlpMaxFreq) {
                    //Not enough time has passed since the last wakelock.  Deny the wakelock
                    param.setResult(null);
                    incrementBlockCount(prefs, param, "NlpWakeLock");

                    debugLog(prefs, "Preventing NlpWakeLock.  Max Interval: " + nlpMaxFreq + " Time since last granted: " + timeSinceLastWakeLock);

                } else {
                    //Allow the wakelock
                    XposedBridge.log(TAG + "Allowing NlpWakeLock.  Max Interval: " + nlpMaxFreq + " Time since last granted: " + timeSinceLastWakeLock);

                    mLastNlpWakeLock = now;
                }
            }
        }
    }

    private void handleAlarm(XSharedPreferences prefs, XC_MethodHook.MethodHookParam param, ArrayList<Object> triggers) {
        prefs.reload();

        for (int j = triggers.size() - 1; j >= 0; j--) {
            Object curAlarm = triggers.get(j);

            PendingIntent pi = (PendingIntent) XposedHelpers.getObjectField(curAlarm, "operation");
            Intent intent = null;
            try {
                intent = (Intent) callMethod(pi, "getIntent");
//                String debugString = intent.toString();
//                XposedBridge.log(TAG + "Debug 4.3+ = " + debugString);
//
//                IntentSender sender = pi.getIntentSender();
//                debugString = sender.toString();
//                XposedBridge.log(TAG + "Debug 4.2.2- = " + debugString);
//
            } catch (NoSuchMethodError nsme) {
                //API prior to 4.2.2_r1 don't have this.
                if (!showedUnsupportedAlarmMessage) {
                    showedUnsupportedAlarmMessage = true;
                    XposedBridge.log(TAG + "Alarm prevention is not yet supported on Android versions less than 4.2.2");
                }
            }

            if(intent==null || intent.getAction()==null){
                //TODO: Why does the system have alarms with null intents?
                continue;
            }

            if (intent.getAction().equals("com.google.android.gms.nlp.ALARM_WAKEUP_LOCATOR")) {
                if (prefs.getBoolean("alarm_locator_enabled", true)) {
                    int locatorMaxFreq = tryParseInt(prefs.getString("alarm_locator_seconds", "240"));
                    locatorMaxFreq *= 1000; //convert to ms

                    //Debounce this to our minimum interval.
                    final long now = SystemClock.elapsedRealtime();
                    long timeSinceLastLocator = now - mLastLocatorAlarm;

                    if (timeSinceLastLocator < locatorMaxFreq) {
                        //Not enough time has passed since the last alarm.  Remove it from the triggerlist
                        triggers.remove(j);
                        incrementBlockCount(prefs, param, "ALARM_WAKEUP_LOCATOR");

                        debugLog(prefs, "Preventing ALARM_WAKEUP_LOCATOR.  Max Interval: " + locatorMaxFreq + " Time since last granted: " + timeSinceLastLocator);
                    } else {
                        //Allow the wakelock
                        XposedBridge.log(TAG + "Allowing ALARM_WAKEUP_LOCATOR.  Max Interval: " + locatorMaxFreq + " Time since last granted: " + timeSinceLastLocator);
                        mLastLocatorAlarm = now;
                    }
                }
            }
            if (intent.getAction().equals("com.google.android.gms.nlp.ALARM_WAKEUP_ACTIVITY_DETECTION")) {
                if (prefs.getBoolean("alarm_detection_enabled", true)) {
                    int detectionMaxFreq = tryParseInt(prefs.getString("alarm_detection_seconds", "240"));
                    detectionMaxFreq *= 1000; //convert to ms

                    //Debounce this to our minimum interval.
                    final long now = SystemClock.elapsedRealtime();
                    long timeSinceLastDetection = now - mLastDetectionAlarm;

                    if (timeSinceLastDetection < detectionMaxFreq) {
                        //Not enough time has passed since the last wakelock.  Remove it from the triggerlist.
                        triggers.remove(j);
                        incrementBlockCount(prefs, param, "ALARM_WAKEUP_ACTIVITY_DETECTION");

                        debugLog(prefs, "Preventing ALARM_WAKEUP_ACTIVITY_DETECTION.  Max Interval: " + detectionMaxFreq + " Time since last granted: " + timeSinceLastDetection);
                    }
                    else {
                        //Allow the wakelock
                        XposedBridge.log(TAG + "Allowing ALARM_WAKEUP_ACTIVITY_DETECTION.  Max Interval: " + detectionMaxFreq + " Time since last granted: " + timeSinceLastDetection);
                        mLastDetectionAlarm = now;
                    }
                }
            }
        }
    }

    private void incrementBlockCount(XSharedPreferences prefs, XC_MethodHook.MethodHookParam param, String name) {

        Context context = (Context)XposedHelpers.getObjectField(param.thisObject, "mContext");

        if (context != null) {
            Intent intent = new Intent("com.ryansteckler.nlpunbounce.INCREMENT_BLOCK_COUNT");
            //TODO:  add FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT to the intent to avoid needing to catch
            //      the IllegalStateException.  The flag value changed between 4.3 and 4.4  :/
            intent.putExtra("name", name);
            try {


                context.sendBroadcast(intent);
            } catch (IllegalStateException ise) {
                //Ignore.  This is becuase boot hasn't completed yet.
            }
        }
    }

    private static int tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException nfe) {
            return 0;
        }
    }

    private void debugLog(XSharedPreferences prefs, String log)
    {
        if (prefs.getBoolean("debug_logging", false))
        {
            XposedBridge.log(TAG + log);
        }
    }
}



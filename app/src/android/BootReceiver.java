package in.daslearning.opensms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Brings the SMS gateway back after a reboot or an app update.
 *
 * The service, not the activity, owns the API server and the SMS receivers, so
 * starting the service is enough for the phone to keep working with no screen
 * ever shown. Android only reaches a manifest receiver once the process is
 * alive, which is why this is Java: a broadcast is the one thing Python cannot
 * be woken by on its own.
 *
 * The service class is generated per build from the app package and the service
 * name in buildozer.spec, so it is looked up by name rather than referenced.
 * This file therefore compiles whatever those settings become, at the cost of
 * one log line if a future p4a renames the generated method.
 *
 * Every outcome is also appended to boot.log, twice over: inside the app's own
 * files, and in the app's external files, which adb can read without root. Some
 * vendor ROMs never surface an app's logcat output, and a phone that comes back
 * from a reboot without its gateway is exactly when that log has to exist.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "OpenSMSBoot";
    private static final String LOG_NAME = "boot.log";
    private static final long LOG_CAP_BYTES = 64 * 1024;

    /** Matches what the activity passes, so the notification reads the same. */
    private static final String SMALL_ICON = "icon";
    private static final String TITLE = "OpenSMS API";
    private static final String TEXT = "Service Running";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        note(context, "woke for " + action);

        // A reboot and an app update leave the same wreckage: the process gone
        // and the service not running. Both get the same answer.
        boolean wanted = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action);
        if (!wanted) {
            note(context, "ignored " + action);
            return;
        }

        try {
            startGateway(context);
            note(context, "gateway service started");
        } catch (Throwable e) {
            // There is nobody to tell at this point, and the app still works the
            // moment it is opened by hand, so this is recorded and left there.
            // Throwable, not Exception: a missing generated class arrives as an
            // Error, and that is the one failure worth having on file.
            note(context, "could not start the gateway service: "
                    + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private void startGateway(Context context) throws Exception {
        Class<?> service = Class.forName(context.getPackageName() + ".ServiceOpensmssvc");

        // The generated builder, rather than a hand-built intent: it is what
        // fills in the private dir, the Python entrypoint and the service flags,
        // and getting any of those wrong starts a service that cannot run.
        Intent intent = (Intent) service
                .getMethod("getDefaultIntent", Context.class, String.class,
                        String.class, String.class, String.class)
                .invoke(null, context, SMALL_ICON, TITLE, TEXT, "");

        // The app is in the background here, and since Android 12 a background
        // start must be a foreground start. Both broadcasts handled above are
        // among the exemptions that allow one.
        context.startForegroundService(intent);
    }

    /** Appends one timestamped line, to stdout and to both copies of boot.log. */
    private void note(Context context, String message) {
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date()) + " " + message + "\n";
        Log.i(TAG, message);
        // Either directory can be unavailable this early after a boot, and the
        // File constructor throws outright on a null one, so both are checked.
        File internal = context.getFilesDir();
        if (internal != null) {
            append(new File(internal, LOG_NAME), line);
        }
        File external = context.getExternalFilesDir(null);
        if (external != null) {
            append(new File(external, LOG_NAME), line);
        }
    }

    private void append(File file, String line) {
        if (file == null || file.getParentFile() == null) {
            return;
        }
        try {
            file.getParentFile().mkdirs();
            if (file.length() > LOG_CAP_BYTES) {
                // Boots are rare, but this file is the only record of them.
                file.delete();
            }
            FileOutputStream out = new FileOutputStream(file, true);
            try {
                out.write(line.getBytes("UTF-8"));
            } finally {
                out.close();
            }
        } catch (IOException | SecurityException e) {
            // Storage can be unavailable this early after a boot. The other
            // copy, and the logcat line above, are why this is not fatal.
            Log.w(TAG, "could not append to " + file, e);
        }
    }
}

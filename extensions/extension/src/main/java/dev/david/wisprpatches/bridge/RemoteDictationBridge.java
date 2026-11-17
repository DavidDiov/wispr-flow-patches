package dev.david.wisprpatches.bridge;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Experimental in-process recording bridge for Wispr Flow 2.8.7.
 * Does not record audio, send network requests, or expose Wispr's private service.
 */
public final class RemoteDictationBridge {
    private static final String TAG = "WisprRemoteBridge";
    public static final String ACTION = "dev.david.wisprpatches.action.TOGGLE_DICTATION";
    private static final String GBOARD = "com.google.android.inputmethod.latin";
    private static final String PIXELBOARD = "com.akshaykadam.pixelboard";
    private static final String SHELL = "com.android.shell";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Service, BroadcastReceiver> RECEIVERS = new IdentityHashMap<>();
    private static Method toggleMethod;

    private RemoteDictationBridge() { }

    public static synchronized void install(final Service service) {
        if (service == null || RECEIVERS.containsKey(service)) return;
        if (Build.VERSION.SDK_INT < 34) {
            Log.w(TAG, "Requires Android 14 or newer for verified broadcast sender");
            return;
        }
        try {
            final BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null || !ACTION.equals(intent.getAction())) return;
                    if (!isAuthorized(getSentFromUid(), getSentFromPackage(), context)) {
                        Log.w(TAG, "Rejected dictation request from unauthorized sender");
                        return;
                    }
                    MAIN.post(() -> toggle(service));
                }
            };
            service.registerReceiver(receiver, new IntentFilter(ACTION), Context.RECEIVER_EXPORTED);
            RECEIVERS.put(service, receiver);
            Log.i(TAG, "Dictation receiver ready");
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to register dictation receiver", e);
        }
    }

    public static synchronized void uninstall(Service service) {
        BroadcastReceiver receiver = RECEIVERS.remove(service);
        if (receiver != null) {
            try {
                service.unregisterReceiver(receiver);
            } catch (RuntimeException e) {
                Log.w(TAG, "Receiver was already unregistered", e);
            }
        }
    }

    private static boolean isAuthorized(int uid, String senderPackage, Context context) {
        if (uid == Process.INVALID_UID || senderPackage == null) return false;
        return GBOARD.equals(senderPackage)
                || PIXELBOARD.equals(senderPackage)
                || SHELL.equals(senderPackage);
    }

    private static void toggle(Service service) {
        try {
            if (toggleMethod == null) {
                Method method = service.getClass().getDeclaredMethod(
                        "handleRecordingToggled", boolean.class, boolean.class);
                method.setAccessible(true);
                toggleMethod = method;
            }
            // Experimental tap equivalent: actual behavior needs device verification.
            toggleMethod.invoke(service, false, false);
            Log.i(TAG, "Dictation toggle dispatched");
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.e(TAG, "Failed to call Wispr's recording toggle", e);
        }
    }
}

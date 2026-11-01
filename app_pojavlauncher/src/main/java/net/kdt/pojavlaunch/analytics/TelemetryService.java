package net.kdt.pojavlaunch.analytics;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Private bridge from the Minecraft process to the Firebase-owning launcher. */
public final class TelemetryService extends Service {
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) Telemetry.receiveGameEvent(intent.getStringExtra("event"), intent.getBundleExtra("params"));
        stopSelf(startId);
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}

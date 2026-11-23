package hu.dowellfx.penzeszsak;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Előtérben futó szolgáltatás: folyamatos kapcsolat az ntfy témához (JSON stream).
 * Minden új üzenetnél bejövő hívást indít.
 */
public class ListenService extends Service {
    static final String CH_SVC = "pz_service";
    static final String CH_CALL = "pz_call";
    static final String CH_MSG = "pz_msg";
    static final int NID_SVC = 1;

    static volatile String status = "Indul…";
    static volatile boolean connected = false;
    private static volatile ListenService self;

    private volatile boolean run;
    private Thread th;
    private volatile HttpURLConnection conn;

    static void start(Context c) {
        Intent i = new Intent(c, ListenService.class);
        try {
            c.startForegroundService(i);
        } catch (Exception e) {
            status = "Nem indult el: " + e.getMessage();
        }
    }

    /** Új beállítás után: a futó kapcsolatot lezárjuk, a ciklus újracsatlakozik. */
    static void reconnect() {
        ListenService s = self;
        if (s != null && s.conn != null) {
            try { s.conn.disconnect(); } catch (Exception ignored) { }
        }
    }

    static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel svc = new NotificationChannel(CH_SVC, "Háttérfigyelés", NotificationManager.IMPORTANCE_MIN);
        svc.setDescription("Csendes, állandó értesítés: a Pénzes Zsák figyel");
        svc.setShowBadge(false);
        nm.createNotificationChannel(svc);

        NotificationChannel call = new NotificationChannel(CH_CALL, "Bejövő hívás (jelzés)", NotificationManager.IMPORTANCE_HIGH);
        call.setDescription("Új jelzésnél teljes képernyős hívás");
        call.setSound(null, null);          // a csengést az app maga játssza le, ismételve
        call.enableVibration(false);        // a rezgést is
        call.setBypassDnd(true);
        call.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(call);

        NotificationChannel msg = new NotificationChannel(CH_MSG, "Nem fogadott hívások", NotificationManager.IMPORTANCE_DEFAULT);
        msg.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(msg);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        self = this;
        channels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = buildSvc(status);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NID_SVC, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NID_SVC, n);
        }
        if (th == null || !th.isAlive()) {
            run = true;
            th = new Thread(this::loop, "pz-listen");
            th.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        run = false;
        reconnect();
        self = null;
        connected = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private Notification buildSvc(String text) {
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CH_SVC)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Pénzes Zsák figyel")
                .setContentText(text)
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(pi)
                .build();
    }

    private void setStatus(String s, boolean ok) {
        if (s.equals(status) && ok == connected) return;
        status = s;
        connected = ok;
        try {
            getSystemService(NotificationManager.class).notify(NID_SVC, buildSvc(s));
        } catch (Exception ignored) { }
    }

    private void loop() {
        int backoff = 2;
        while (run) {
            String topic = P.topic(this);
            if (topic.isEmpty()) {
                setStatus("Nincs beállítva téma – nyisd meg az appot", false);
                sleep(5000);
                continue;
            }
            HttpURLConnection c = null;
            try {
                String since = P.lastId(this);
                String u = P.server(this) + "/" + URLEncoder.encode(topic, "UTF-8") + "/json"
                        + (since.isEmpty() ? "" : "?since=" + URLEncoder.encode(since, "UTF-8"));
                c = (HttpURLConnection) new URL(u).openConnection();
                conn = c;
                c.setConnectTimeout(15000);
                c.setReadTimeout(130000);   // az ntfy ~45 mp-enként keepalive-ot küld
                c.setRequestProperty("User-Agent", "PenzesZsak/1.0 (Android)");
                int code = c.getResponseCode();
                if (code != 200) throw new IOException("HTTP " + code);
                setStatus("Kapcsolódva ✓ – várja a jelzést", true);
                backoff = 2;
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while (run && (line = r.readLine()) != null) handle(line);
                if (run) throw new IOException("a szerver lezárta");
            } catch (Exception e) {
                if (run) setStatus("Újrakapcsolódás… (" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ")", false);
            } finally {
                if (c != null) try { c.disconnect(); } catch (Exception ignored) { }
                conn = null;
            }
            if (run) {
                sleep(backoff * 1000L);
                backoff = Math.min(backoff * 2, 60);
            }
        }
    }

    private void handle(String line) {
        try {
            JSONObject j = new JSONObject(line);
            if (!"message".equals(j.optString("event"))) return;
            String id = j.optString("id", "");
            if (!id.isEmpty()) P.setLastId(this, id);
            long t = j.optLong("time", System.currentTimeMillis() / 1000);
            String msg = j.optString("message", "");
            String click = j.optString("click", "");
            P.addHistory(this, t, msg);
            PowerManager pm = getSystemService(PowerManager.class);
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pz:ring");
            wl.acquire(10000);
            boolean fresh = System.currentTimeMillis() / 1000 - t < 15 * 60;   // régi (pótolt) üzenetre nem csörgünk
            if (fresh) Caller.ring(this, msg, click);
            else Caller.missed(this, msg, click);
        } catch (Exception ignored) { }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}

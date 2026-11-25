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
 * Előtérben futó szolgáltatás: folyamatos kapcsolat a fiók saját értesítési csatornájához.
 * Új üzenetnél: letölti az üzeneteket, és ha belépő / időstop, hívásként csörög.
 * Félóránként ellenőrzi a fiókot: ha lejárt, kijelentkeztet és leáll.
 */
public class ListenService extends Service {
    static final String CH_SVC = "pz_service";
    static final String CH_CALL = "pz_call";
    static final String CH_MSG = "pz_msg";
    static final String CH_INFO = "pz_info2";   // felugró, egyet csippanó értesítés (nem hívás)
    static final int NID_SVC = 1;
    private static final long CHECK_MS = 30 * 60 * 1000L;

    static volatile String status = "Indul…";
    static volatile boolean connected = false;
    /** Ha nő, a nyitott képernyők frissítik az üzenetlistát. */
    static volatile int feedVersion = 0;
    private static volatile ListenService self;

    private volatile boolean run;
    private Thread th;
    private volatile HttpURLConnection conn;
    private long lastCheck = 0;

    static void start(Context c) {
        if (!P.loggedIn(c)) return;
        try {
            c.startForegroundService(new Intent(c, ListenService.class));
        } catch (Exception e) {
            status = "Nem indult el: " + e.getMessage();
        }
    }

    /** A futó kapcsolatot lezárjuk, a ciklus újracsatlakozik (vagy leáll, ha kijelentkeztél). */
    static void reconnect() {
        ListenService s = self;
        if (s != null && s.conn != null) {
            try { s.conn.disconnect(); } catch (Exception ignored) { }
        }
    }

    static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel svc = new NotificationChannel(CH_SVC, "Háttérfigyelés", NotificationManager.IMPORTANCE_MIN);
        svc.setDescription("Csendes, állandó értesítés: a DowellFX figyel");
        svc.setShowBadge(false);
        nm.createNotificationChannel(svc);

        NotificationChannel call = new NotificationChannel(CH_CALL, "Bejövő hívás (jelzés)", NotificationManager.IMPORTANCE_HIGH);
        call.setDescription("Új belépőnél és időstopnál teljes képernyős hívás");
        call.setSound(null, null);          // a csengést az app maga játssza le, ismételve
        call.enableVibration(false);
        call.setBypassDnd(true);
        call.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(call);

        NotificationChannel msg = new NotificationChannel(CH_MSG, "Nem fogadott hívások", NotificationManager.IMPORTANCE_DEFAULT);
        msg.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(msg);

        try { nm.deleteNotificationChannel("pz_info"); } catch (Exception ignored) { }   // a régi, néma csatorna
        NotificationChannel info = new NotificationChannel(CH_INFO, "Zárások és összesítők", NotificationManager.IMPORTANCE_HIGH);
        info.setDescription("Nyertes/vesztes zárás, összesítők – felugrik és egyet csippan, de nem csörög");
        info.enableVibration(true);
        info.setVibrationPattern(new long[]{0, 250});
        info.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(info);
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
        if (Build.VERSION.SDK_INT >= 34) startForeground(NID_SVC, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NID_SVC, n);
        if (!P.loggedIn(this)) { stopSelf(); return START_NOT_STICKY; }
        if (th == null || !th.isAlive()) {
            run = true;
            th = new Thread(this::loop, "dfx-listen");
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
                .setContentTitle("DowellFX figyel")
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
        try { getSystemService(NotificationManager.class).notify(NID_SVC, buildSvc(s)); } catch (Exception ignored) { }
    }

    /** Fiók ellenőrzése + üzenetek letöltése. false, ha a fiók megszűnt. */
    private boolean checkAccount() {
        lastCheck = System.currentTimeMillis();
        int n = Api.syncFeed(this);
        if (n < 0) return false;
        if (n > 0) feedVersion++;
        return P.loggedIn(this);
    }

    private void loop() {
        int backoff = 2;
        if (!checkAccount()) { finish(); return; }
        while (run) {
            if (!P.loggedIn(this)) { finish(); return; }
            if (System.currentTimeMillis() - lastCheck > CHECK_MS && !checkAccount()) { finish(); return; }
            HttpURLConnection c = null;
            try {
                String since = P.lastId(this);
                String u = P.relay(this) + "/" + URLEncoder.encode(P.channel(this), "UTF-8") + "/json"
                        + (since.isEmpty() ? "" : "?since=" + URLEncoder.encode(since, "UTF-8"));
                c = (HttpURLConnection) new URL(u).openConnection();
                conn = c;
                c.setConnectTimeout(15000);
                c.setReadTimeout(130000);   // a továbbító ~45 mp-enként életjelet küld
                c.setRequestProperty("User-Agent", "DowellFX-Android/" + BuildInfo.VERSION);
                int code = c.getResponseCode();
                if (code != 200) throw new IOException("HTTP " + code);
                setStatus("Kapcsolódva ✓ – várja a jelzést", true);
                backoff = 2;
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while (run && (line = r.readLine()) != null) {
                    handle(line);
                    if (System.currentTimeMillis() - lastCheck > CHECK_MS) break;   // félóránként fiók-ellenőrzés
                }
                if (run && System.currentTimeMillis() - lastCheck <= CHECK_MS) throw new IOException("a kapcsolat megszakadt");
            } catch (Exception e) {
                if (run && P.loggedIn(this)) setStatus("Újrakapcsolódás… (" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ")", false);
            } finally {
                if (c != null) try { c.disconnect(); } catch (Exception ignored) { }
                conn = null;
            }
            if (run && P.loggedIn(this) && System.currentTimeMillis() - lastCheck <= CHECK_MS) {
                sleep(backoff * 1000L);
                backoff = Math.min(backoff * 2, 60);
            }
        }
    }

    private void finish() {
        run = false;
        connected = false;
        status = "Kijelentkezve";
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void handle(String line) {
        try {
            JSONObject j = new JSONObject(line);
            if (!"message".equals(j.optString("event"))) return;
            String rid = j.optString("id", "");
            if (!rid.isEmpty()) P.setLastId(this, rid);
            long t = j.optLong("time", System.currentTimeMillis() / 1000);
            String msg = j.optString("message", "");
            int id = 0;
            String click = j.optString("click", "");
            if (click.startsWith("dowellfx://kick")) {        // máshol beléptek ezzel a fiókkal: azonnali ellenőrzés
                if (Api.syncFeed(this) < 0) finish();
                return;
            }
            int k = click.lastIndexOf('/');
            if (click.startsWith("dowellfx://m/") && k > 0) try { id = Integer.parseInt(click.substring(k + 1)); } catch (Exception ignored) { }
            PowerManager pm = getSystemService(PowerManager.class);
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dfx:ring");
            wl.acquire(15000);
            boolean ring = j.optInt("priority", 3) >= 4;
            boolean fresh = System.currentTimeMillis() / 1000 - t < 15 * 60;   // régi (pótolt) üzenetre nem csörgünk
            if (ring && fresh) Caller.ring(this, msg, id);
            else if (ring) Caller.missed(this, msg, id);
            else Caller.quiet(this, msg, id);
            // az üzenetlista frissítése (a csengés közben, a háttérben)
            int n = Api.syncFeed(this);
            if (n < 0) { finish(); return; }
            feedVersion++;
        } catch (Exception ignored) { }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}

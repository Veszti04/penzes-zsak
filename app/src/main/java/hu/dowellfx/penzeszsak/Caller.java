package hu.dowellfx.penzeszsak;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Bejövő „hívás”: teljes képernyős értesítés + ismétlődő csengés + rezgés, amíg fel nem veszed / el nem utasítod. */
final class Caller {
    private Caller() {}

    static final int NID_CALL = 2;
    static final int NID_MISSED = 3;
    private static final long RING_MS = 60_000;
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static MediaPlayer mp;
    private static Vibrator vib;
    private static String lastMsg = "";
    private static int lastIdx = 0;
    private static Context app;
    static volatile boolean ringing = false;

    private static final Runnable TIMEOUT = () -> {
        Context c = app;
        stop(c);
        if (c != null) missed(c, lastMsg, lastIdx);
    };

    static String firstLines(String msg, int n) {
        String[] ls = msg.split("\n");
        StringBuilder b = new StringBuilder();
        int k = 0;
        for (String l : ls) {
            String t = l.trim();
            if (t.isEmpty() || t.contains("────")) continue;
            if (b.length() > 0) b.append(" · ");
            b.append(t);
            if (++k >= n) break;
        }
        return b.toString();
    }

    /** Az app üzenetlistája, az adott üzenetre görgetve. */
    static Intent chatIntent(Context c, int id, boolean fromCall) {
        return new Intent(c, MainActivity.class).putExtra("focus", id).putExtra("fromCall", fromCall)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    static Intent callIntent(Context c, String msg, int id, boolean answered) {
        return new Intent(c, CallActivity.class)
                .putExtra("msg", msg).putExtra("id", id).putExtra("answered", answered)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
    }

    static void ring(Context ctx, String msg, int id) {
        final Context c = ctx.getApplicationContext();
        app = c;
        lastMsg = msg;
        lastIdx = id;
        ListenService.channels(c);
        H.post(() -> {
            H.removeCallbacks(TIMEOUT);
            startSound(c);
            startVibration(c);
            ringing = true;
            H.postDelayed(TIMEOUT, RING_MS);
        });

        // Kijelző bekapcsolása + a hívásképernyő közvetlen megnyitása a lezárt képernyő fölött
        // (ehhez a „Megjelenítés más alkalmazások felett” engedély kell; enélkül a teljes képernyős értesítés intézi)
        wakeScreen(c);
        if (Settings.canDrawOverlays(c)) {
            try { c.startActivity(callIntent(c, msg, id, false)); } catch (Exception ignored) { }
        }

        int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent full = PendingIntent.getActivity(c, 10, callIntent(c, msg, id, false), fl);
        PendingIntent answer = PendingIntent.getActivity(c, 11, callIntent(c, msg, id, true), fl);
        PendingIntent decline = PendingIntent.getBroadcast(c, 12, new Intent(c, Rx.class).setAction(Rx.DECLINE), fl);

        Notification.Builder b = new Notification.Builder(c, ListenService.CH_CALL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(P.callerName(c) + " hív")
                .setContentText(firstLines(msg, 4))
                .setCategory(Notification.CATEGORY_CALL)
                .setFullScreenIntent(full, true)
                .setContentIntent(full)
                .setOngoing(true)
                .setAutoCancel(false)
                .setColor(0xFFF2C14E)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setTimeoutAfter(RING_MS + 2000);
        if (Build.VERSION.SDK_INT >= 31) {
            android.graphics.Bitmap ph = P.callerPhoto(c);
            Person caller = new Person.Builder()
                    .setName(P.callerName(c))
                    .setIcon(ph != null ? Icon.createWithBitmap(Ui.circle(ph)) : Icon.createWithResource(c, R.drawable.logo))
                    .setImportant(true)
                    .build();
            b.setStyle(Notification.CallStyle.forIncomingCall(caller, decline, answer));
        } else {
            b.addAction(new Notification.Action.Builder((Icon) null, "Elutasít", decline).build());
            b.addAction(new Notification.Action.Builder((Icon) null, "Felveszem", answer).build());
        }
        try {
            c.getSystemService(NotificationManager.class).notify(NID_CALL, b.build());
        } catch (Exception e) {
            // ha a CallStyle valamiért nem engedélyezett, egyszerű értesítéssel próbáljuk
            try {
                b.setStyle(null);
                c.getSystemService(NotificationManager.class).notify(NID_CALL, b.build());
            } catch (Exception ignored) { }
        }
    }

    @SuppressWarnings("deprecation")
    private static void wakeScreen(Context c) {
        try {
            PowerManager pm = c.getSystemService(PowerManager.class);
            if (pm != null && !pm.isInteractive()) {
                PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "dfx:wake");
                wl.acquire(8000);
            }
        } catch (Exception ignored) { }
    }

    static void stop(Context c) {
        H.removeCallbacks(TIMEOUT);
        ringing = false;
        try { if (mp != null) { mp.stop(); mp.release(); } } catch (Exception ignored) { }
        mp = null;
        try { if (vib != null) vib.cancel(); } catch (Exception ignored) { }
        if (c != null) {
            try { c.getSystemService(NotificationManager.class).cancel(NID_CALL); } catch (Exception ignored) { }
        }
    }

    static void missed(Context ctx, String msg, int id) {
        Context c = ctx.getApplicationContext();
        PendingIntent pi = PendingIntent.getActivity(c, 13, chatIntent(c, id, false),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, ListenService.CH_MSG)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Nem fogadott hívás: " + P.callerName(c))
                .setContentText(firstLines(msg, 4))
                .setStyle(new Notification.BigTextStyle().bigText(msg))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setColor(0xFFF2C14E)
                .build();
        try { c.getSystemService(NotificationManager.class).notify(NID_MISSED, n); } catch (Exception ignored) { }
    }

    /** Sima értesítés (zárás, összesítő): felugrik és egyet csippan, de nem csörög és nincs hívásképernyő. */
    static void quiet(Context ctx, String msg, int id) {
        Context c = ctx.getApplicationContext();
        ListenService.channels(c);
        PendingIntent pi = PendingIntent.getActivity(c, 14 + (id % 1000), chatIntent(c, id, false),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String[] ls = msg.split("\n");
        String title = ls.length > 0 ? ls[0].trim() : "DowellFX";
        Notification n = new Notification.Builder(c, ListenService.CH_INFO)
                .setSmallIcon(R.drawable.ic_stat)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setOnlyAlertOnce(false)
                .setContentTitle(title.isEmpty() ? "DowellFX" : title)
                .setContentText(firstLines(msg.substring(Math.min(msg.length(), ls.length > 0 ? ls[0].length() : 0)), 3))
                .setStyle(new Notification.BigTextStyle().bigText(msg))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setColor(0xFFF2C14E)
                .build();
        try { c.getSystemService(NotificationManager.class).notify((int) (System.currentTimeMillis() % 100000) + 100, n); } catch (Exception ignored) { }
    }

    /** Jelzés-adatok kiolvasása a szövegből: [fajta, irány, szimbólum, belépő, SL, most / új stop]. */
    static String[] parse(String msg) {
        String kind = msg.contains("IDŐSTOP") ? "timestop" : msg.contains("KÖVETŐ STOP") ? "stop" : msg.contains("PRÓBA") ? "test" : (msg.contains("SZIGNÁL") || msg.contains("BELÉPÉSI")) ? "signal" : "other";
        String dir = "", sym = "", entry = "", sl = "", now = "";
        for (String raw : msg.split("\n")) {
            String l = raw.trim();
            if (dir.isEmpty() && l.startsWith("📍")) dir = l.contains("SELL") ? "SELL" : l.contains("BUY") ? "BUY" : "";
            else if (sym.isEmpty() && l.startsWith("🥇")) sym = l.substring(l.indexOf("🥇") + 2).trim();
            else if (entry.isEmpty() && l.startsWith("Belépő:")) entry = l.substring(7).trim();
            else if (sl.isEmpty() && (l.startsWith("SL:") || l.startsWith("Eredeti SL:"))) sl = l.substring(l.indexOf(':') + 1).trim();
            else if (now.isEmpty() && l.startsWith("Most:")) now = l.substring(5).trim();
            else if (now.isEmpty() && l.contains("Új stop:")) now = l.substring(l.indexOf("Új stop:") + 8).trim();
        }
        return new String[]{kind, dir, sym, entry, sl, now};
    }

    private static void startSound(Context c) {
        try { if (mp != null) { mp.stop(); mp.release(); } } catch (Exception ignored) { }
        mp = null;
        try {
            MediaPlayer p = new MediaPlayer();
            p.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(P.alarm(c) ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            if (P.systemTone(c)) {
                p.setDataSource(c, RingtoneManager.getActualDefaultRingtoneUri(c, RingtoneManager.TYPE_RINGTONE));
            } else {
                AssetFileDescriptor afd = c.getResources().openRawResourceFd(R.raw.ring);
                p.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            }
            p.setLooping(true);
            p.prepare();
            p.start();
            mp = p;
        } catch (Exception e) {
            // végső tartalék: a beépített hang
            try {
                MediaPlayer p = MediaPlayer.create(c, R.raw.ring);
                if (p != null) { p.setLooping(true); p.start(); mp = p; }
            } catch (Exception ignored) { }
        }
    }

    private static void startVibration(Context c) {
        try {
            vib = c.getSystemService(Vibrator.class);
            if (vib == null || !vib.hasVibrator()) return;
            long[] pattern = {0, 900, 500, 900, 500, 900, 1200};
            AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build();
            vib.vibrate(VibrationEffect.createWaveform(pattern, 0), aa);
        } catch (Exception ignored) { }
    }
}

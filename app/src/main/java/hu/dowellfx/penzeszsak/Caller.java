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
    private static String lastMsg = "", lastClick = "";
    private static Context app;
    static volatile boolean ringing = false;

    private static final Runnable TIMEOUT = () -> {
        Context c = app;
        stop(c);
        if (c != null) missed(c, lastMsg, lastClick);
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

    static Intent callIntent(Context c, String msg, String click, boolean answered) {
        return new Intent(c, CallActivity.class)
                .putExtra("msg", msg).putExtra("click", click).putExtra("answered", answered)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
    }

    static void ring(Context ctx, String msg, String click) {
        final Context c = ctx.getApplicationContext();
        app = c;
        lastMsg = msg;
        lastClick = click;
        ListenService.channels(c);
        H.post(() -> {
            H.removeCallbacks(TIMEOUT);
            startSound(c);
            startVibration(c);
            ringing = true;
            H.postDelayed(TIMEOUT, RING_MS);
        });

        int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent full = PendingIntent.getActivity(c, 10, callIntent(c, msg, click, false), fl);
        PendingIntent answer = PendingIntent.getActivity(c, 11, callIntent(c, msg, click, true), fl);
        PendingIntent decline = PendingIntent.getBroadcast(c, 12, new Intent(c, Rx.class).setAction(Rx.DECLINE), fl);

        Notification.Builder b = new Notification.Builder(c, ListenService.CH_CALL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Pénzes Zsák hív")
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
            Person caller = new Person.Builder()
                    .setName("Pénzes Zsák")
                    .setIcon(Icon.createWithResource(c, R.drawable.coin))
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

    static void missed(Context ctx, String msg, String click) {
        Context c = ctx.getApplicationContext();
        PendingIntent pi = PendingIntent.getActivity(c, 13, callIntent(c, msg, click, true),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, ListenService.CH_MSG)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Nem fogadott hívás: Pénzes Zsák")
                .setContentText(firstLines(msg, 4))
                .setStyle(new Notification.BigTextStyle().bigText(msg))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setColor(0xFFF2C14E)
                .build();
        try { c.getSystemService(NotificationManager.class).notify(NID_MISSED, n); } catch (Exception ignored) { }
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

package hu.dowellfx.penzeszsak;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** Kijelentkezés: kézzel, vagy amikor a szerver szerint a fiók lejárt / törölték. */
final class Account {
    private Account() {}

    static final String MSG_EXPIRED = "Hibás adatok vagy már lejárt a meghívód!";

    /** A fiók megszűnt: azonnal leállunk, hogy ne jöjjön több értesítés. */
    static void expired(Context ctx) {
        Context c = ctx.getApplicationContext();
        if (!P.loggedIn(c)) return;
        stopAll(c);
        P.sp(c).edit().putString("loginMsg", "A hozzáférésed lejárt vagy megszűnt, ezért kijelentkeztettünk. Új meghívókóddal regisztrálhatsz újra.").apply();
        try {
            PendingIntent pi = PendingIntent.getActivity(c, 30, new Intent(c, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(c, ListenService.CH_MSG)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle("DowellFX: lejárt a hozzáférésed")
                    .setContentText("Kijelentkeztettünk, nem kapsz több jelzést.")
                    .setContentIntent(pi).setAutoCancel(true).setColor(0xFFF2C14E).build();
            ListenService.channels(c);
            c.getSystemService(NotificationManager.class).notify(40, n);
        } catch (Exception ignored) { }
    }

    /** Kézi kijelentkezés (a szerveren is töröljük a telefon tokenjét). */
    static void logout(Context ctx) {
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            try { Api.call(c, "logout", null, new org.json.JSONObject()); } catch (Exception ignored) { }
        }).start();
        stopAll(c);
    }

    private static void stopAll(Context c) {
        Caller.stop(c);
        P.clearAccount(c);
        try { c.stopService(new Intent(c, ListenService.class)); } catch (Exception ignored) { }
        ListenService.reconnect();
        try { c.getSystemService(NotificationManager.class).cancelAll(); } catch (Exception ignored) { }
    }
}

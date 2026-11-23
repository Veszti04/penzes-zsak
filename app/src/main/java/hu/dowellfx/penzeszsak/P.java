package hu.dowellfx.penzeszsak;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Beállítások és üzenet-előzmények (SharedPreferences). */
final class P {
    private P() {}

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("pz", Context.MODE_PRIVATE);
    }

    static String server(Context c) {
        String s = sp(c).getString("server", "https://ntfy.sh").trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s.isEmpty() ? "https://ntfy.sh" : s;
    }

    static String topic(Context c) { return sp(c).getString("topic", "").trim(); }
    static String lastId(Context c) { return sp(c).getString("lastId", ""); }
    static void setLastId(Context c, String id) { sp(c).edit().putString("lastId", id).apply(); }
    /** Néma módban is csörögjön (ébresztő hangerővel szól). */
    static boolean alarm(Context c) { return sp(c).getBoolean("alarm", true); }
    /** A telefon saját csengőhangja az app hangja helyett. */
    static boolean systemTone(Context c) { return sp(c).getBoolean("systemTone", false); }

    /** A hívó neve a hívásképernyőn (alapból DowellFX). */
    static String callerName(Context c) {
        String s = sp(c).getString("callerName", "").trim();
        return s.isEmpty() ? "DowellFX" : s;
    }
    static void setCallerName(Context c, String s) { sp(c).edit().putString("callerName", s.trim()).apply(); }

    /** A hívó képe (a felhasználó választja a galériából); null, ha nincs. */
    static java.io.File callerPhotoFile(Context c) { return new java.io.File(c.getFilesDir(), "caller.png"); }
    static android.graphics.Bitmap callerPhoto(Context c) {
        java.io.File f = callerPhotoFile(c);
        if (!f.exists()) return null;
        try { return android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath()); } catch (Exception e) { return null; }
    }

    static void save(Context c, String server, String topic) {
        sp(c).edit().putString("server", server.trim()).putString("topic", topic.trim()).putString("lastId", "").apply();
    }

    static void addHistory(Context c, long time, String msg) {
        try {
            JSONArray a = new JSONArray(sp(c).getString("hist", "[]"));
            JSONArray b = new JSONArray();
            b.put(new JSONObject().put("t", time).put("m", msg));
            for (int i = 0; i < a.length() && i < 29; i++) b.put(a.get(i));
            sp(c).edit().putString("hist", b.toString()).apply();
        } catch (Exception ignored) { }
    }

    static JSONArray history(Context c) {
        try { return new JSONArray(sp(c).getString("hist", "[]")); } catch (Exception e) { return new JSONArray(); }
    }
}

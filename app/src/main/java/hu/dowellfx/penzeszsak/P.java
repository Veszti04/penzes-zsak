package hu.dowellfx.penzeszsak;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Beállítások, fiók és a helyben tárolt üzenetek (SharedPreferences). */
final class P {
    private P() {}

    static final String DEFAULT_SERVER = "https://retromc.hu/dowellforex/";

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("pz", Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- fiók
    static String server(Context c) {
        String s = sp(c).getString("srv", DEFAULT_SERVER).trim();
        if (s.isEmpty()) s = DEFAULT_SERVER;
        return s.endsWith("/") ? s : s + "/";
    }
    static void setServer(Context c, String s) { sp(c).edit().putString("srv", s == null ? "" : s.trim()).apply(); }
    static String token(Context c) { return sp(c).getString("tok", ""); }
    static boolean loggedIn(Context c) { return !token(c).isEmpty() && !channel(c).isEmpty(); }
    static String user(Context c) { return sp(c).getString("user", ""); }
    static String role(Context c) { return sp(c).getString("role", "user"); }
    static long expires(Context c) { return sp(c).getLong("exp", 0); }
    static String channel(Context c) { return sp(c).getString("ch", ""); }
    static String relay(Context c) {
        String s = sp(c).getString("relay", "https://ntfy.sh");
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** A szerver válasza (login / pair / me) alapján elmenti a fiók adatait. */
    static void saveAccount(Context c, JSONObject j, boolean withToken) {
        SharedPreferences.Editor e = sp(c).edit()
                .putString("user", j.optString("user", ""))
                .putString("role", j.optString("role", "user"))
                .putLong("exp", j.optLong("expires", 0));
        String ch = j.optString("channel", "");
        if (!ch.isEmpty() && !ch.equals(channel(c))) e.putString("ch", ch).putString("lastId", "");
        String relay = j.optString("relay", "");
        if (!relay.isEmpty()) e.putString("relay", relay);
        if (withToken) e.putString("tok", j.optString("token", ""));
        e.apply();
    }

    /** Kijelentkezés: a fiók és az üzenetek törlődnek, a hívó beállításai maradnak. */
    static void clearAccount(Context c) {
        sp(c).edit().remove("tok").remove("user").remove("role").remove("exp").remove("ch")
                .remove("lastId").remove("feed").remove("feedLast").apply();
    }

    static String lastId(Context c) { return sp(c).getString("lastId", ""); }
    static void setLastId(Context c, String id) { sp(c).edit().putString("lastId", id).apply(); }

    // ---------------------------------------------------------------- hívás
    /** Néma módban is csörögjön (ébresztő hangerővel szól). */
    static boolean alarm(Context c) { return sp(c).getBoolean("alarm", true); }
    /** A telefon saját csengőhangja az app hangja helyett. */
    static boolean systemTone(Context c) { return sp(c).getBoolean("systemTone", false); }

    static String callerName(Context c) {
        String s = sp(c).getString("callerName", "").trim();
        return s.isEmpty() ? "DowellFX" : s;
    }
    static void setCallerName(Context c, String s) { sp(c).edit().putString("callerName", s.trim()).apply(); }
    static java.io.File callerPhotoFile(Context c) { return new java.io.File(c.getFilesDir(), "caller.png"); }
    static android.graphics.Bitmap callerPhoto(Context c) {
        java.io.File f = callerPhotoFile(c);
        if (!f.exists()) return null;
        try { return android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath()); } catch (Exception e) { return null; }
    }

    // ---------------------------------------------------------------- üzenetek (a szerver feedjének helyi másolata)
    static synchronized JSONArray feed(Context c) {
        try { return new JSONArray(sp(c).getString("feed", "[]")); } catch (Exception e) { return new JSONArray(); }
    }
    static int feedLast(Context c) { return sp(c).getInt("feedLast", 0); }

    /** Új elemek hozzáfűzése (id szerint, ismétlés nélkül), legfeljebb 150 marad. Vissza: hány új jött. */
    static synchronized int mergeFeed(Context c, JSONArray items) {
        try {
            JSONArray cur = feed(c);
            int last = feedLast(c), added = 0;
            java.util.HashSet<Integer> ids = new java.util.HashSet<>();
            for (int i = 0; i < cur.length(); i++) ids.add(cur.getJSONObject(i).optInt("id"));
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                int id = o.optInt("id");
                if (id <= 0 || ids.contains(id)) continue;
                cur.put(o); ids.add(id); added++;
                last = Math.max(last, id);
            }
            if (cur.length() > 150) {
                JSONArray cut = new JSONArray();
                for (int i = cur.length() - 150; i < cur.length(); i++) cut.put(cur.get(i));
                cur = cut;
            }
            sp(c).edit().putString("feed", cur.toString()).putInt("feedLast", last).apply();
            return added;
        } catch (Exception e) { return 0; }
    }
}

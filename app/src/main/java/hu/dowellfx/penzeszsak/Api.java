package hu.dowellfx.penzeszsak;

import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** A weboldal API-ja (api.php). Csak háttérszálról hívható. */
final class Api {
    private Api() {}

    /** Vissza: a JSON válasz, kiegészítve "_code" (HTTP kód) mezővel; hálózati hibánál _code = 0. */
    static JSONObject call(Context c, String action, String query, JSONObject body) {
        HttpURLConnection h = null;
        try {
            String u = P.server(c) + "api.php?a=" + action + (query == null ? "" : "&" + query);
            h = (HttpURLConnection) new URL(u).openConnection();
            h.setConnectTimeout(12000);
            h.setReadTimeout(20000);
            h.setRequestProperty("User-Agent", "DowellFX-Android/" + BuildInfo.VERSION);
            h.setRequestProperty("Accept", "application/json");
            String tok = P.token(c);
            if (!tok.isEmpty()) h.setRequestProperty("X-DFX-Token", tok);
            if (body != null) {
                body.put("device", Build.MANUFACTURER + " " + Build.MODEL);
                byte[] b = body.toString().getBytes(StandardCharsets.UTF_8);
                h.setDoOutput(true);
                h.setRequestMethod("POST");
                h.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream o = h.getOutputStream()) { o.write(b); }
            }
            int code = h.getResponseCode();
            InputStream in = code >= 400 ? h.getErrorStream() : h.getInputStream();
            String txt = in == null ? "" : read(in);
            JSONObject j;
            try { j = new JSONObject(txt); } catch (Exception e) { j = new JSONObject().put("ok", false).put("error", "Váratlan szerverválasz (HTTP " + code + ")"); }
            j.put("_code", code);
            return j;
        } catch (Exception e) {
            JSONObject j = new JSONObject();
            try { j.put("ok", false).put("_code", 0).put("error", "Nincs kapcsolat a szerverrel. Ellenőrizd az internetet."); } catch (Exception ignored) { }
            return j;
        } finally {
            if (h != null) h.disconnect();
        }
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    /** Lejárt / törölt fiók: a szerver 401-et ad. */
    static boolean denied(JSONObject j) { return j.optInt("_code") == 401; }

    /** Üzenetek letöltése a legutóbbi óta. Vissza: új üzenetek száma, vagy -1 ha a fiók megszűnt. */
    static int syncFeed(Context c) {
        JSONObject j = call(c, "feed", "since=" + P.feedLast(c), null);
        if (denied(j)) { Account.expired(c, j.optString("error", null)); return -1; }
        if (!j.optBoolean("ok")) return 0;
        P.saveAccount(c, j, false);
        JSONArray items = j.optJSONArray("items");
        return items == null ? 0 : P.mergeFeed(c, items);
    }
}

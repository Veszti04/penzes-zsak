package hu.dowellfx.penzeszsak;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Beállító képernyő: kapcsolat, engedélyek, próbahívás, legutóbbi jelzések. */
public class MainActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView status, permNotif, permFull, permBatt;
    private EditText server, topic;
    private LinearLayout hist;
    private int histCount = -1;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refreshStatus();
            h.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        ListenService.channels(this);
        build();
        handleLink(getIntent());
        if (!P.topic(this).isEmpty()) ListenService.start(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handleLink(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPerms();
        h.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }

    /** penzeszsak://setup?server=https://ntfy.sh&topic=... – a weboldal gombja tölti ki. */
    private void handleLink(Intent i) {
        Uri u = i == null ? null : i.getData();
        if (u == null || !"penzeszsak".equals(u.getScheme())) return;
        String t = u.getQueryParameter("topic");
        String s = u.getQueryParameter("server");
        if (t == null || t.trim().isEmpty()) return;
        if (s == null || s.trim().isEmpty()) s = "https://ntfy.sh";
        P.save(this, s, t);
        server.setText(P.server(this));
        topic.setText(P.topic(this));
        ListenService.start(this);
        ListenService.reconnect();
        Toast.makeText(this, "✅ Beállítva a weboldalról", Toast.LENGTH_LONG).show();
    }

    private void build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        root.setPadding(p, Ui.dp(this, 28), p, Ui.dp(this, 28));
        sv.addView(root);

        // fejléc
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView coin = new ImageView(this);
        coin.setImageResource(R.drawable.coin);
        head.addView(coin, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.setPadding(Ui.dp(this, 12), 0, 0, 0);
        ht.addView(Ui.text(this, "Pénzes Zsák", 22, Ui.TEXT, true));
        status = Ui.text(this, "…", 13, Ui.MUTED, false);
        ht.addView(status);
        head.addView(ht, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(head);

        // próbahívás
        Button test = Ui.button(this, "📞 Próbahívás 5 mp múlva (zárd le a telefont)", true);
        test.setOnClickListener(v -> {
            Toast.makeText(this, "Zárd le a telefont – 5 mp múlva csörög", Toast.LENGTH_LONG).show();
            h.postDelayed(() -> Caller.ring(getApplicationContext(),
                    "🧪 PRÓBAHÍVÁS\nHa ezt látod és csörgött a telefon, minden működik. 💰", ""), 5000);
        });
        root.addView(test, Ui.lp(this, 18));

        // engedélyek
        LinearLayout perms = Ui.card(this);
        perms.addView(Ui.text(this, "Hogy mindig csörögjön", 16, Ui.TEXT, true));
        permNotif = permRow(perms, "Értesítések engedélyezése", v -> {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            else openAppNotif();
        });
        permFull = permRow(perms, "Teljes képernyős hívás engedélyezése", v -> {
            if (Build.VERSION.SDK_INT >= 34) {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName()))); }
                catch (Exception e) { openAppNotif(); }
            } else openAppNotif();
        });
        permBatt = permRow(perms, "Akkumulátor: korlátlan futás", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        });
        Switch alarm = sw(perms, "Néma / rezgő módban is csörögjön (ébresztő hangerővel)", P.alarm(this),
                (bt, on) -> P.sp(this).edit().putBoolean("alarm", on).apply());
        Switch tone = sw(perms, "A telefon saját csengőhangja (különben a Pénzes Zsák csengés)", P.systemTone(this),
                (bt, on) -> P.sp(this).edit().putBoolean("systemTone", on).apply());
        root.addView(perms, Ui.lp(this, 14));

        // kapcsolat
        LinearLayout conn = Ui.card(this);
        conn.addView(Ui.text(this, "Kapcsolat", 16, Ui.TEXT, true));
        TextView hint = Ui.text(this, "A legegyszerűbb: a weboldalon az app „Csörgés lezárt képernyőn” részénél nyomd meg az „📲 APK beállítása” gombot. Vagy írd be kézzel:", 13, Ui.MUTED, false);
        conn.addView(hint, Ui.lp(this, 6));
        server = field(conn, "Szerver", P.server(this));
        topic = field(conn, "Téma (penzeszsak-…)", P.topic(this));
        Button save = Ui.button(this, "Mentés és kapcsolódás", false);
        save.setOnClickListener(v -> {
            if (topic.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Add meg a témát", Toast.LENGTH_SHORT).show();
                return;
            }
            P.save(this, server.getText().toString(), topic.getText().toString());
            ListenService.start(this);
            ListenService.reconnect();
            Toast.makeText(this, "Mentve", Toast.LENGTH_SHORT).show();
        });
        conn.addView(save, Ui.lp(this, 10));
        root.addView(conn, Ui.lp(this, 14));

        // előzmények
        LinearLayout hc = Ui.card(this);
        hc.addView(Ui.text(this, "Legutóbbi jelzések", 16, Ui.TEXT, true));
        hist = new LinearLayout(this);
        hist.setOrientation(LinearLayout.VERTICAL);
        hc.addView(hist);
        root.addView(hc, Ui.lp(this, 14));

        setContentView(sv);
    }

    private TextView permRow(LinearLayout parent, String label, android.view.View.OnClickListener l) {
        TextView t = Ui.text(this, "…  " + label, 15, Ui.TEXT, false);
        t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        t.setOnClickListener(l);
        t.setTag(label);
        parent.addView(t, Ui.lp(this, 4));
        return t;
    }

    private Switch sw(LinearLayout parent, String label, boolean on, CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(Ui.TEXT);
        s.setTextSize(14);
        s.setChecked(on);
        s.setOnCheckedChangeListener(l);
        s.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        parent.addView(s, Ui.lp(this, 4));
        return s;
    }

    private EditText field(LinearLayout parent, String label, String value) {
        parent.addView(Ui.text(this, label, 12, Ui.MUTED, false), Ui.lp(this, 10));
        EditText e = new EditText(this);
        e.setText(value);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(Ui.MUTED);
        e.setBackground(Ui.round(0xFF0B130E, Ui.LINE, 10, this));
        int q = Ui.dp(this, 10);
        e.setPadding(q, q, q, q);
        parent.addView(e, Ui.lp(this, 4));
        return e;
    }

    private void openAppNotif() {
        startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }

    private void mark(TextView t, boolean ok) {
        t.setText((ok ? "✅  " : "❌  ") + t.getTag() + (ok ? "" : "  – koppints ide"));
        t.setTextColor(ok ? Ui.TEXT : 0xFFFFC2C8);
    }

    private void refreshPerms() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        mark(permNotif, nm.areNotificationsEnabled());
        mark(permFull, Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent());
        PowerManager pm = getSystemService(PowerManager.class);
        mark(permBatt, pm.isIgnoringBatteryOptimizations(getPackageName()));
    }

    private void refreshStatus() {
        status.setText((ListenService.connected ? "● " : "○ ") + ListenService.status);
        status.setTextColor(ListenService.connected ? Ui.GREEN : Ui.MUTED);
        JSONArray a = P.history(this);
        if (a.length() == histCount) return;
        histCount = a.length();
        hist.removeAllViews();
        if (a.length() == 0) {
            hist.addView(Ui.text(this, "Még nem jött jelzés.", 14, Ui.MUTED, false), Ui.lp(this, 8));
            return;
        }
        SimpleDateFormat f = new SimpleDateFormat("MM.dd. HH:mm", new Locale("hu", "HU"));
        for (int i = 0; i < a.length() && i < 15; i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            final String m = o.optString("m");
            TextView t = Ui.text(this, f.format(new Date(o.optLong("t") * 1000)) + "   " + Caller.firstLines(m, 4), 14, Ui.TEXT, false);
            t.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            t.setOnClickListener(v -> startActivity(Caller.callIntent(this, m, "", true)));
            hist.addView(t, Ui.lp(this, 2));
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        refreshPerms();
        if (!P.topic(this).isEmpty()) ListenService.start(this);
    }
}

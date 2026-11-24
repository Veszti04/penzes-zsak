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
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Beállítások: fiók, próbahívások, ki hívjon, engedélyek, kijelentkezés. */
public class SettingsActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView status, permNotif, permFull, permBatt, permOverlay;
    private LinearLayout avatarBox;
    private EditText callerName;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refreshStatus();
            h.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!P.loggedIn(this)) { startActivity(new Intent(this, LoginActivity.class)); finish(); return; }
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        ListenService.channels(this);
        build();
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
        TextView back = Ui.text(this, "‹", 30, Ui.TEXT, true);
        back.setPadding(0, 0, Ui.dp(this, 14), Ui.dp(this, 4));
        back.setOnClickListener(v -> finish());
        back.setContentDescription("Vissza");
        head.addView(back);
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.addView(Ui.text(this, "Beállítások", 24, Ui.TEXT, true));
        status = Ui.text(this, "…", 13, Ui.MUTED, false);
        ht.addView(status);
        head.addView(ht, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(head);

        // fiók
        LinearLayout acc = Ui.card(this);
        acc.addView(Ui.text(this, "Fiók", 16, Ui.TEXT, true));
        long exp = P.expires(this);
        String left = exp > 0 ? Math.max(0, (int) Math.ceil((exp - System.currentTimeMillis() / 1000.0) / 86400.0)) + " nap"
                : "nem jár le (admin)";
        acc.addView(Ui.text(this, "👤 " + P.user(this) + (exp > 0 ? "\n⏳ Hozzáférés: még " + left + " (" + new SimpleDateFormat("yyyy.MM.dd. HH:mm", new Locale("hu", "HU")).format(new Date(exp * 1000)) + "-ig)" : "\n⏳ Hozzáférés: " + left)
                + "\n🌐 " + P.server(this), 14, Ui.TEXT, false), Ui.lp(this, 8));
        root.addView(acc, Ui.lp(this, 16));

        // próbahívások
        LinearLayout tc = Ui.card(this);
        tc.addView(Ui.text(this, "Hívás kipróbálása", 16, Ui.TEXT, true));
        tc.addView(Ui.text(this, "Nyomd meg, majd zárd le a telefont. A szerveres próba az egész utat teszteli: weboldal → telefon.", 13, Ui.MUTED, false), Ui.lp(this, 6));
        Button test = Ui.button(this, "📞 Próbahívás 5 mp múlva (kikapcsolt képernyőn)", true);
        test.setOnClickListener(v -> {
            Toast.makeText(this, "Zárd le a telefont – 5 mp múlva csörög", Toast.LENGTH_LONG).show();
            h.postDelayed(() -> Caller.ring(getApplicationContext(),
                    "📣 ÚJ SZIGNÁL! (PRÓBA)\n🥇 XAUUSD\n📍 BUY\nBelépő: 4 100,00\nSL: 4 088,00\nCél: Követő stop\n\n🧪 Ez csak próbahívás – ha csörgött, minden működik. 💰", 0), 5000);
        });
        tc.addView(test, Ui.lp(this, 12));
        Button stest = Ui.button(this, "🌐 Próbahívás a szerverről", false);
        stest.setOnClickListener(v -> {
            stest.setEnabled(false);
            new Thread(() -> {
                JSONObject r = Api.call(getApplicationContext(), "test", null, new JSONObject());
                runOnUiThread(() -> {
                    stest.setEnabled(true);
                    if (Api.denied(r)) { Account.expired(this); startActivity(new Intent(this, LoginActivity.class)); finish(); return; }
                    Toast.makeText(this, r.optBoolean("ok") ? "Elküldve – zárd le a telefont, mindjárt csörög" : r.optString("error", "Hiba"), Toast.LENGTH_LONG).show();
                });
            }).start();
        });
        tc.addView(stest, Ui.lp(this, 10));
        root.addView(tc, Ui.lp(this, 14));

        // hívó (név + kép)
        LinearLayout who = Ui.card(this);
        who.addView(Ui.text(this, "Ki hívjon?", 16, Ui.TEXT, true));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        avatarBox = new LinearLayout(this);
        avatarBox.addView(Ui.avatar(this, 72));
        row.addView(avatarBox);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(this, 14), 0, 0, 0);
        callerName = field(col, "Hívó neve", P.callerName(this));
        callerName.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence a, int b, int c, int d) { }
            @Override public void onTextChanged(CharSequence a, int b, int c, int d) { }
            @Override public void afterTextChanged(android.text.Editable e) { P.setCallerName(SettingsActivity.this, e.toString()); }
        });
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        who.addView(row, Ui.lp(this, 8));
        LinearLayout btns = new LinearLayout(this);
        Button pick = Ui.button(this, "🖼 Kép választása", false);
        pick.setOnClickListener(v -> {
            try { startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), 7); }
            catch (Exception e) { Toast.makeText(this, "Nincs képválasztó", Toast.LENGTH_SHORT).show(); }
        });
        btns.addView(pick, new LinearLayout.LayoutParams(0, -2, 1f));
        View gap = new View(this);
        btns.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 10), 1));
        Button reset = Ui.button(this, "DowellFX logó", false);
        reset.setOnClickListener(v -> {
            P.callerPhotoFile(this).delete();
            refreshAvatar();
        });
        btns.addView(reset, new LinearLayout.LayoutParams(0, -2, 1f));
        who.addView(btns, Ui.lp(this, 12));
        root.addView(who, Ui.lp(this, 14));

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
        permOverlay = permRow(perms, "Hívás a lezárt képernyő fölött (megjelenítés más appok felett)", v -> {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
            catch (Exception e) { openAppDetails(); }
        });
        if (Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("xiaomi") || Build.BRAND.toLowerCase(Locale.ROOT).matches(".*(redmi|poco|xiaomi).*")) {
            TextView mi = Ui.text(this, "ℹ️  Xiaomi/Redmi: Egyéb engedélyek → „Megjelenítés a lezárt képernyőn” és „Felugró ablakok a háttérben” – koppints ide", 14, Ui.GOLD, false);
            mi.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            mi.setOnClickListener(v -> {
                try {
                    startActivity(new Intent("miui.intent.action.APP_PERM_EDITOR")
                            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                            .putExtra("extra_pkgname", getPackageName()));
                } catch (Exception e) { openAppDetails(); }
            });
            perms.addView(mi, Ui.lp(this, 4));
        }
        permBatt = permRow(perms, "Akkumulátor: korlátlan futás", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        });
        Switch alarm = sw(perms, "Néma / rezgő módban is csörögjön (ébresztő hangerővel)", P.alarm(this),
                (bt, on) -> P.sp(this).edit().putBoolean("alarm", on).apply());
        Switch tone = sw(perms, "A telefon saját csengőhangja (különben a DowellFX csengés)", P.systemTone(this),
                (bt, on) -> P.sp(this).edit().putBoolean("systemTone", on).apply());
        root.addView(perms, Ui.lp(this, 14));

        // párosítás és kijelentkezés
        LinearLayout lo = Ui.card(this);
        lo.addView(Ui.text(this, "Párosítás", 16, Ui.TEXT, true));
        lo.addView(Ui.text(this, "Az app ehhez a fiókhoz van párosítva. Másik fiókhoz: jelentkezz ki, majd lépj be újra (vagy a weboldal App menüjében koppints a Párosítás gombra).", 13, Ui.MUTED, false), Ui.lp(this, 6));
        Button out = Ui.button(this, "Kijelentkezés", false);
        out.setTextColor(0xFFFFC2C8);
        out.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("Kijelentkezel?")
                .setMessage("Amíg nem lépsz be újra, nem kapsz jelzést.")
                .setNegativeButton("Mégsem", null)
                .setPositiveButton("Kijelentkezés", (d, w) -> {
                    Account.logout(this);
                    startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                }).show());
        lo.addView(out, Ui.lp(this, 12));
        root.addView(lo, Ui.lp(this, 14));
        TextView ver = Ui.text(this, "DowellFX " + BuildInfo.VERSION, 12, Ui.MUTED, false);
        ver.setGravity(Gravity.CENTER);
        root.addView(ver, Ui.lp(this, 18));

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

    private void openAppDetails() {
        try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) { }
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
        mark(permOverlay, Settings.canDrawOverlays(this));
    }

    private void refreshStatus() {
        if (!P.loggedIn(this)) { startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish(); return; }
        status.setText((ListenService.connected ? "● " : "○ ") + ListenService.status);
        status.setTextColor(ListenService.connected ? Ui.GREEN : Ui.MUTED);
    }

    private void refreshAvatar() {
        avatarBox.removeAllViews();
        avatarBox.addView(Ui.avatar(this, 72));
    }

    /** A választott képet négyzetre vágva (max 512 px) elmentjük az app saját tárhelyére. */
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 7 || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            android.graphics.Bitmap bm;
            Uri u = data.getData();
            if (Build.VERSION.SDK_INT >= 28) {
                bm = android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(getContentResolver(), u), (dec, info, src) -> {
                    int big = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                    dec.setTargetSampleSize(Math.max(1, big / 1024));
                    dec.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            } else {
                android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                o.inSampleSize = 2;
                try (java.io.InputStream in = getContentResolver().openInputStream(u)) { bm = android.graphics.BitmapFactory.decodeStream(in, null, o); }
            }
            if (bm == null) throw new Exception("nem olvasható");
            int n = Math.min(bm.getWidth(), bm.getHeight());
            android.graphics.Bitmap sq = android.graphics.Bitmap.createBitmap(bm, (bm.getWidth() - n) / 2, (bm.getHeight() - n) / 2, n, n);
            if (n > 512) sq = android.graphics.Bitmap.createScaledBitmap(sq, 512, 512, true);
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(P.callerPhotoFile(this))) {
                sq.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            }
            refreshAvatar();
            Toast.makeText(this, "✅ Kép beállítva", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Nem sikerült betölteni a képet: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        refreshPerms();
        ListenService.start(this);
    }
}

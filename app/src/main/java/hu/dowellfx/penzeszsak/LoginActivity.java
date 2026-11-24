package hu.dowellfx.penzeszsak;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

/** Belépés a weboldalon regisztrált fiókkal, vagy párosítás a weboldal „Párosítás” gombjával. */
public class LoginActivity extends Activity {
    private EditText user, pass, server;
    private TextView err;
    private Button go;
    private LinearLayout adv;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        if (P.loggedIn(this) && !isPair(getIntent())) { openChat(); return; }
        build();
        String msg = P.sp(this).getString("loginMsg", "");
        if (!msg.isEmpty()) { showErr(msg); P.sp(this).edit().remove("loginMsg").apply(); }
        handlePair(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (err == null) build();
        handlePair(i);
    }

    private static boolean isPair(Intent i) {
        Uri u = i == null ? null : i.getData();
        return u != null && "dowellfx".equals(u.getScheme()) && "pair".equals(u.getHost());
    }

    private void build() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF132219, 0xFF0A120E, 0xFF050806}));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(this, 26);
        root.setPadding(p, Ui.dp(this, 70), p, p);
        sv.addView(root);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        root.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 112)));
        TextView h = Ui.text(this, "DowellFX", 30, Ui.TEXT, true);
        h.setGravity(Gravity.CENTER);
        root.addView(h, Ui.lp(this, 14));
        TextView sub = Ui.text(this, "Lépj be a weboldalon regisztrált fiókoddal", 14, Ui.MUTED, false);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, Ui.lp(this, 4));

        err = Ui.text(this, "", 14, 0xFFFFD5DB, false);
        err.setBackground(Ui.round(0xFF4A1F26, 0xFF8B3A46, 12, this));
        int q = Ui.dp(this, 12);
        err.setPadding(q, q, q, q);
        err.setVisibility(View.GONE);
        root.addView(err, Ui.lp(this, 22));

        user = field(root, "Felhasználónév", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        user.setText(P.sp(this).getString("lastUser", ""));
        pass = field(root, "Jelszó", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pass.setImeOptions(EditorInfo.IME_ACTION_DONE);
        pass.setOnEditorActionListener((v, a, e) -> { login(); return true; });

        go = Ui.button(this, "Belépés", true);
        go.setOnClickListener(v -> login());
        root.addView(go, Ui.lp(this, 20));

        TextView reg = Ui.text(this, "Nincs még fiókod? Regisztrálj a weboldalon a meghívókódoddal.", 13, Ui.MUTED, false);
        reg.setGravity(Gravity.CENTER);
        reg.setOnClickListener(v -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(P.server(this)))); } catch (Exception ignored) { } });
        root.addView(reg, Ui.lp(this, 18));

        TextView more = Ui.text(this, "Szerver beállítása ›", 12, 0xFF6F8A7A, false);
        more.setGravity(Gravity.CENTER);
        root.addView(more, Ui.lp(this, 26));
        adv = new LinearLayout(this);
        adv.setOrientation(LinearLayout.VERTICAL);
        adv.setVisibility(View.GONE);
        server = field(adv, "Weboldal címe", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        server.setText(P.server(this));
        root.addView(adv, new LinearLayout.LayoutParams(-1, -2));
        more.setOnClickListener(v -> adv.setVisibility(adv.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        setContentView(sv);
    }

    private EditText field(LinearLayout parent, String label, int type) {
        TextView l = Ui.text(this, label, 13, Ui.MUTED, false);
        parent.addView(l, Ui.lp(this, 14));
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setTextColor(Ui.TEXT);
        e.setTextSize(17);
        e.setBackground(Ui.round(0xFF0B130E, Ui.LINE, 12, this));
        int q = Ui.dp(this, 13);
        e.setPadding(q, q, q, q);
        parent.addView(e, Ui.lp(this, 6));
        return e;
    }

    private void showErr(String m) {
        err.setText(m);
        err.setVisibility(m.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void busy(boolean b, String label) {
        go.setEnabled(!b);
        go.setText(label);
        go.setAlpha(b ? 0.6f : 1f);
    }

    private void login() {
        final String u = user.getText().toString().trim(), pw = pass.getText().toString();
        if (u.isEmpty() || pw.isEmpty()) { showErr("Add meg a felhasználónevet és a jelszót."); return; }
        P.setServer(this, server.getText().toString());
        showErr("");
        busy(true, "Belépés…");
        new Thread(() -> {
            JSONObject body = new JSONObject();
            try { body.put("user", u).put("pass", pw); } catch (Exception ignored) { }
            JSONObject r = Api.call(getApplicationContext(), "login", null, body);
            runOnUiThread(() -> done(r, u));
        }).start();
    }

    /** dowellfx://pair?s=<weboldal>&c=<kód> – a weboldal „Párosítás” gombja. */
    private void handlePair(Intent i) {
        if (!isPair(i)) return;
        Uri u = i.getData();
        final String s = u.getQueryParameter("s"), code = u.getQueryParameter("c");
        if (code == null || code.isEmpty()) return;
        if (s != null && s.startsWith("http")) { P.setServer(this, s); if (server != null) server.setText(P.server(this)); }
        if (P.loggedIn(this)) Account.logout(this);   // másik fiókra váltás
        showErr("");
        busy(true, "Párosítás…");
        new Thread(() -> {
            JSONObject body = new JSONObject();
            try { body.put("code", code); } catch (Exception ignored) { }
            JSONObject r = Api.call(getApplicationContext(), "pair", null, body);
            runOnUiThread(() -> done(r, null));
        }).start();
    }

    private void done(JSONObject r, String u) {
        busy(false, "Belépés");
        if (!r.optBoolean("ok") || r.optString("token", "").isEmpty()) {
            showErr(r.optInt("_code") == 401 ? r.optString("error", Account.MSG_EXPIRED) : r.optString("error", "Nem sikerült a belépés."));
            return;
        }
        P.saveAccount(this, r, true);
        if (u != null) P.sp(this).edit().putString("lastUser", u).apply();
        ListenService.start(this);
        openChat();
    }

    private void openChat() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }
}

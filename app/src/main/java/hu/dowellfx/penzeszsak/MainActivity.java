package hu.dowellfx.penzeszsak;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Az app fő képernyője: a jelzések chatként, mint a Telegramban. A számokra koppintva vágólapra kerülnek. */
public class MainActivity extends Activity {
    private static final Pattern NUM = Pattern.compile("[+\\u2212-]?\\d{1,3}(?:[ \\u00a0\\u202f]\\d{3})+(?:,\\d+)?|[+\\u2212-]?\\d+(?:,\\d+)?");
    private static final Locale HU = new Locale("hu", "HU");

    private final Handler h = new Handler(Looper.getMainLooper());
    private ScrollView scroll;
    private LinearLayout list;
    private TextView status, empty;
    private final HashMap<Integer, View> rendered = new HashMap<>();
    private String lastDay = "";
    private int seenVersion = -1;
    private int focusId = 0;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!P.loggedIn(MainActivity.this)) { toLogin(); return; }
            refreshStatus();
            if (seenVersion != ListenService.feedVersion) { seenVersion = ListenService.feedVersion; render(); }
            h.postDelayed(this, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!P.loggedIn(this)) { toLogin(); return; }
        getWindow().setStatusBarColor(0xFF0B1410);
        getWindow().setNavigationBarColor(Ui.BG);
        ListenService.channels(this);
        build();
        handleIntent(getIntent());
        ListenService.start(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handleIntent(i);
    }

    private void handleIntent(Intent i) {
        boolean fromCall = i != null && i.getBooleanExtra("fromCall", false);
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(fromCall);   // felvett hívás után a zár fölött is látszik
        focusId = i == null ? 0 : i.getIntExtra("focus", 0);
        if (focusId > 0) { render(); h.postDelayed(this::applyFocus, 250); }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!P.loggedIn(this)) { toLogin(); return; }
        render();
        h.post(tick);
        new Thread(() -> {
            int n = Api.syncFeed(getApplicationContext());
            runOnUiThread(() -> {
                if (n < 0 || !P.loggedIn(this)) { toLogin(); return; }
                render();
                if (focusId > 0) applyFocus();
            });
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }

    private void toLogin() {
        startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    // ---------------------------------------------------------------- felület
    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(0xFF0B1410);
        int p = Ui.dp(this, 12);
        top.setPadding(Ui.dp(this, 14), p, Ui.dp(this, 8), p);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        top.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(Ui.dp(this, 12), 0, 0, 0);
        tt.addView(Ui.text(this, "DowellFX", 19, Ui.TEXT, true));
        status = Ui.text(this, "…", 12.5f, Ui.MUTED, false);
        status.setSingleLine(true);
        tt.addView(status);
        top.addView(tt, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView gear = Ui.text(this, "⚙", 24, Ui.TEXT, false);
        gear.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        gear.setContentDescription("Beállítások");
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        top.addView(gear);
        root.addView(top);
        View line = new View(this);
        line.setBackgroundColor(Ui.LINE);
        root.addView(line, new LinearLayout.LayoutParams(-1, Ui.dp(this, 1)));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int q = Ui.dp(this, 12);
        list.setPadding(q, q, q, Ui.dp(this, 20));
        TextView hint = Ui.text(this, "Koppints egy számra – vágólapra kerül (pl. belépő, SL, lot)", 12, Ui.MUTED, false);
        hint.setGravity(Gravity.CENTER);
        list.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        empty = Ui.text(this, "Még nincs üzenet.\nAmint a bot jelez, itt jelenik meg – belépőnél a telefon csörög is.", 15, Ui.MUTED, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, Ui.dp(this, 80), 0, 0);
        list.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private void refreshStatus() {
        long exp = P.expires(this);
        String acc = P.user(this) + (exp > 0 ? " · még " + Math.max(0, (int) Math.ceil((exp - System.currentTimeMillis() / 1000.0) / 86400.0)) + " nap" : "");
        status.setText((ListenService.connected ? "● online · " : "○ kapcsolódás… · ") + acc);
        status.setTextColor(ListenService.connected ? Ui.GREEN : Ui.MUTED);
    }

    /** Új üzenetek hozzáfűzése a listához (a már kirajzoltakat nem rajzolja újra). */
    private void render() {
        if (list == null) return;
        JSONArray a = P.feed(this);
        boolean atBottom = !scroll.canScrollVertically(1);
        int added = 0;
        SimpleDateFormat day = new SimpleDateFormat("MMMM d., EEEE", HU);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            int id = o.optInt("id");
            if (rendered.containsKey(id)) continue;
            long at = o.optLong("at") * 1000;
            String d = day.format(new Date(at));
            if (!d.equals(lastDay)) {
                lastDay = d;
                TextView sep = Ui.text(this, d, 12, Ui.MUTED, false);
                sep.setBackground(Ui.round(0xFF101A15, Ui.LINE, 99, this));
                sep.setPadding(Ui.dp(this, 12), Ui.dp(this, 3), Ui.dp(this, 12), Ui.dp(this, 3));
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
                sp.gravity = Gravity.CENTER_HORIZONTAL;
                sp.topMargin = Ui.dp(this, 14);
                list.addView(sep, sp);
            }
            View v = bubble(o);
            rendered.put(id, v);
            added++;
        }
        empty.setVisibility(rendered.isEmpty() ? View.VISIBLE : View.GONE);
        if (added > 0 && (atBottom || added == rendered.size()) && focusId == 0) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private int kindColor(String plain) {
        if (plain.contains("IDŐSTOP")) return Ui.ORANGE;
        if (plain.contains("SZIGNÁL")) return Ui.GREEN;
        if (plain.contains("NYERTES")) return Ui.GOLD;
        if (plain.contains("VESZTES")) return Ui.RED;
        if (plain.contains("STOP")) return 0xFF5AA9FF;
        return 0xFF5C7466;
    }

    private View bubble(JSONObject o) {
        String html = o.optString("html", "").replace("\r", "").replace("\n", "<br>");
        Spanned sp = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        SpannableStringBuilder sb = new SpannableStringBuilder(sp);
        Matcher m = NUM.matcher(sb.toString());
        while (m.find()) {
            final String raw = m.group();
            final String val = raw.replaceAll("[ \\u00a0\\u202f]", "").replace('−', '-').replace(',', '.').replaceFirst("^\\+", "");
            sb.setSpan(new ClickableSpan() {
                @Override public void onClick(View w) { copy(val); }
                @Override public void updateDrawState(TextPaint ds) { ds.setColor(0xFFFFE08A); ds.setUnderlineText(true); }
            }, m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(Ui.round(Ui.PANEL, Ui.LINE, 14, this));
        View bar = new View(this);
        bar.setBackgroundColor(kindColor(sb.toString()));
        row.addView(bar, new LinearLayout.LayoutParams(Ui.dp(this, 4), -1));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 12);
        col.setPadding(p, Ui.dp(this, 10), p, Ui.dp(this, 8));
        TextView t = Ui.text(this, "", 15.5f, Ui.TEXT, false);
        t.setText(sb);
        t.setMovementMethod(LinkMovementMethod.getInstance());
        t.setHighlightColor(Color.TRANSPARENT);
        t.setLineSpacing(0, 1.2f);
        col.addView(t);
        TextView time = Ui.text(this, new SimpleDateFormat("HH:mm", HU).format(new Date(o.optLong("at") * 1000)), 11, Ui.MUTED, false);
        time.setGravity(Gravity.END);
        col.addView(time, new LinearLayout.LayoutParams(-1, -2));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        row.setClipToOutline(true);

        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, 10);
        list.addView(wrap, lp);
        return wrap;
    }

    private void applyFocus() {
        if (focusId <= 0) return;
        final View v = rendered.get(focusId);
        if (v == null) return;
        final int id = focusId;
        focusId = 0;
        scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, v.getTop() - Ui.dp(this, 40))));
        v.setForeground(Ui.round(0x00000000, Ui.GOLD, 14, this));
        ((android.graphics.drawable.GradientDrawable) v.getForeground()).setStroke(Ui.dp(this, 2), Ui.GOLD);
        h.postDelayed(() -> { if (rendered.get(id) == v) v.setForeground(null); }, 3500);
    }

    private void copy(String val) {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("DowellFX", val));
        Toast.makeText(this, "📋 Másolva: " + val, Toast.LENGTH_SHORT).show();
        try {
            Vibrator vb = getSystemService(Vibrator.class);
            if (vb != null) vb.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) { }
    }
}

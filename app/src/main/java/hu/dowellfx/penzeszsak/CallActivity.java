package hu.dowellfx.penzeszsak;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Teljes képernyős bejövő hívás (DowellFX): hívó képe + neve, a jelzés lényege, Felveszem / Elutasít. Lezárt képernyő fölött is. */
public class CallActivity extends Activity {
    private String msg = "";
    private int id = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(0xFF132219);
        getWindow().setNavigationBarColor(Ui.BG);
        load(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        load(i);
    }

    private void load(Intent i) {
        msg = i.getStringExtra("msg") == null ? "" : i.getStringExtra("msg");
        id = i.getIntExtra("id", 0);
        if (i.getBooleanExtra("answered", false) || !Caller.ringing) answer();
        else showRinging();
    }

    /** Felveszem: csend, és az app üzenetlistája nyílik meg, ennél a jelzésnél. */
    private void answer() {
        Caller.stop(this);
        startActivity(Caller.chatIntent(this, id, true));
        finishAndRemoveTask();
    }

    private GradientDrawable bg() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF132219, 0xFF0A120E, 0xFF050806});
        return g;
    }

    private void showRinging() {
        String[] s = Caller.parse(msg);   // kind, dir, sym, entry, sl, now
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackground(bg());
        int p = Ui.dp(this, 24);
        root.setPadding(p, Ui.dp(this, 44), p, Ui.dp(this, 44));

        // márka-sor
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView lg = new ImageView(this);
        lg.setImageResource(R.drawable.logo);
        brand.addView(lg, new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
        TextView bt = Ui.text(this, "  DowellFX · bejövő hívás", 14, 0xFFD9C58F, false);
        brand.addView(bt);
        root.addView(brand);

        // hívó képe pulzáló gyűrűkkel
        boolean photo = Ui.photoAvatar(this);
        FrameLayout av = new FrameLayout(this);
        LinearLayout.LayoutParams avp = new LinearLayout.LayoutParams(Ui.dp(this, 200), Ui.dp(this, 200));
        avp.topMargin = Ui.dp(this, 30);
        av.setLayoutParams(avp);
        for (int k = 0; k < 3; k++) {
            View ring = new View(this);
            GradientDrawable g = new GradientDrawable();
            if (photo) g.setShape(GradientDrawable.OVAL); else g.setCornerRadius(Ui.dp(this, 52));
            g.setStroke(Ui.dp(this, 2), 0x88F2C14E);
            ring.setBackground(g);
            FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(Ui.dp(this, 160), Ui.dp(this, 160), Gravity.CENTER);
            av.addView(ring, rp);
            pulse(ring, k * 700L);
        }
        ImageView face = Ui.avatar(this, 160);
        face.setElevation(Ui.dp(this, 10));
        av.addView(face, new FrameLayout.LayoutParams(Ui.dp(this, 160), Ui.dp(this, 160), Gravity.CENTER));
        root.addView(av);

        TextView name = Ui.text(this, P.callerName(this), 36, Ui.TEXT, true);
        name.setGravity(Gravity.CENTER);
        root.addView(name, Ui.lp(this, 10));
        TextView sub = Ui.text(this, "hív…", 17, Ui.MUTED, false);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, Ui.lp(this, 2));

        // jelzés lényege
        LinearLayout chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER);
        String dir = s[1];
        if ("timestop".equals(s[0])) chips.addView(Ui.pill(this, "⏱ IDŐSTOP – ZÁRD!", Ui.ORANGE));
        else if (!dir.isEmpty()) chips.addView(Ui.pill(this, dir.equals("BUY") ? "▲ BUY" : "▼ SELL", dir.equals("BUY") ? Ui.GREEN : Ui.RED));
        else if ("test".equals(s[0])) chips.addView(Ui.pill(this, "PRÓBAHÍVÁS", Ui.GOLD));
        if (!s[2].isEmpty()) {
            TextView sym = Ui.pill(this, s[2], Ui.GOLD);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
            sp.leftMargin = Ui.dp(this, 8);
            chips.addView(sym, sp);
        }
        root.addView(chips, Ui.lp(this, 22));

        if (!s[3].isEmpty() || !s[4].isEmpty()) {
            LinearLayout tiles = new LinearLayout(this);
            tiles.addView(tile("Belépő", s[3]), new LinearLayout.LayoutParams(0, -2, 1f));
            View gap = new View(this);
            tiles.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 10), 1));
            tiles.addView(tile("timestop".equals(s[0]) ? "Most" : "Stop loss", "timestop".equals(s[0]) ? s[5] : s[4]), new LinearLayout.LayoutParams(0, -2, 1f));
            root.addView(tiles, Ui.lp(this, 14));
        } else {
            TextView prev = Ui.text(this, Caller.firstLines(msg, 5), 14, 0xFFCFDCD2, false);
            prev.setGravity(Gravity.CENTER);
            root.addView(prev, Ui.lp(this, 14));
        }

        root.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout acts = new LinearLayout(this);
        acts.addView(action("✕", "Elutasít", Ui.RED, v -> { Caller.stop(this); finishAndRemoveTask(); }), new LinearLayout.LayoutParams(0, -2, 1f));
        View ans = action("📞", "Felveszem", Ui.GREEN, v -> answer());
        acts.addView(ans, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(acts, Ui.lp(this, 0));
        ObjectAnimator bob = ObjectAnimator.ofFloat(ans, "translationY", 0f, -Ui.dp(this, 7));
        bob.setDuration(500);
        bob.setRepeatMode(ValueAnimator.REVERSE);
        bob.setRepeatCount(ValueAnimator.INFINITE);
        bob.start();
        setContentView(root);
    }

    private View tile(String label, String value) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.round(0x14FFFFFF, 0x22FFFFFF, 16, this));
        int q = Ui.dp(this, 12);
        t.setPadding(q, q, q, q);
        TextView l = Ui.text(this, label, 12, Ui.MUTED, false);
        l.setGravity(Gravity.CENTER);
        t.addView(l);
        TextView v = Ui.text(this, value.isEmpty() ? "–" : value, 20, Ui.TEXT, true);
        v.setGravity(Gravity.CENTER);
        t.addView(v);
        return t;
    }

    private void pulse(View v, long delay) {
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setAlpha(0.9f);
        v.animate().scaleX(1.25f).scaleY(1.25f).alpha(0f).setDuration(2000).setStartDelay(delay)
                .withEndAction(() -> { if (!isFinishing()) pulse(v, 0); }).start();
    }

    private View action(String icon, String label, int color, View.OnClickListener l) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView c = Ui.circle(this, icon, color, 76, 30);
        c.setElevation(Ui.dp(this, 6));
        col.addView(c);
        TextView t = Ui.text(this, label, 14, Ui.MUTED, false);
        t.setGravity(Gravity.CENTER);
        col.addView(t, Ui.lp(this, 8));
        c.setOnClickListener(l);
        col.setOnClickListener(l);
        c.setContentDescription(label);
        return col;
    }

    @Override
    public void onBackPressed() {
        if (Caller.ringing) Caller.stop(this);
        finishAndRemoveTask();
    }
}

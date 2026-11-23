package hu.dowellfx.penzeszsak;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Color;
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

/** Teljes képernyős bejövő hívás: „Pénzes Zsák hív…”, Felveszem / Elutasít. Lezárt képernyő fölött is megjelenik. */
public class CallActivity extends Activity {
    private String msg = "", click = "";

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
        getWindow().setStatusBarColor(Ui.BG);
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
        click = i.getStringExtra("click") == null ? "" : i.getStringExtra("click");
        if (i.getBooleanExtra("answered", false) || !Caller.ringing) {
            Caller.stop(this);
            showDetails();
        } else {
            showRinging();
        }
    }

    private void showRinging() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Ui.BG);
        int p = Ui.dp(this, 24);
        root.setPadding(p, Ui.dp(this, 56), p, Ui.dp(this, 48));

        TextView lbl = Ui.text(this, "Bejövő hívás · DowellFX", 15, 0xFFD9C58F, false);
        root.addView(lbl);

        FrameLayout av = new FrameLayout(this);
        LinearLayout.LayoutParams avp = new LinearLayout.LayoutParams(Ui.dp(this, 170), Ui.dp(this, 170));
        avp.topMargin = Ui.dp(this, 40);
        av.setLayoutParams(avp);
        for (int k = 0; k < 3; k++) {
            View ring = new View(this);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setStroke(Ui.dp(this, 2), 0x88F2C14E);
            ring.setBackground(g);
            av.addView(ring, new FrameLayout.LayoutParams(-1, -1));
            pulse(ring, k * 700L);
        }
        ImageView coin = new ImageView(this);
        coin.setImageResource(R.drawable.coin);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(Ui.dp(this, 150), Ui.dp(this, 150), Gravity.CENTER);
        av.addView(coin, cp);
        root.addView(av);

        TextView name = Ui.text(this, "Pénzes Zsák", 34, Ui.TEXT, true);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = Ui.lp(this, 24);
        root.addView(name, np);
        TextView sub = Ui.text(this, "hív…", 17, Ui.MUTED, false);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, Ui.lp(this, 4));

        TextView prev = Ui.text(this, Caller.firstLines(msg, 6), 14, 0xFFCFDCD2, false);
        prev.setGravity(Gravity.CENTER);
        prev.setBackground(Ui.round(0x14FFFFFF, 0x22FFFFFF, 14, this));
        int q = Ui.dp(this, 12);
        prev.setPadding(q, q, q, q);
        root.addView(prev, Ui.lp(this, 24));

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout acts = new LinearLayout(this);
        acts.setOrientation(LinearLayout.HORIZONTAL);
        acts.addView(action("✕", "Elutasít", Ui.RED, v -> { Caller.stop(this); finishAndRemoveTask(); }), new LinearLayout.LayoutParams(0, -2, 1f));
        View ans = action("📞", "Felveszem", Ui.GREEN, v -> { Caller.stop(this); showDetails(); });
        acts.addView(ans, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(acts, Ui.lp(this, 0));
        ObjectAnimator bob = ObjectAnimator.ofFloat(ans, "translationY", 0f, -Ui.dp(this, 7));
        bob.setDuration(500);
        bob.setRepeatMode(ValueAnimator.REVERSE);
        bob.setRepeatCount(ValueAnimator.INFINITE);
        bob.start();
        setContentView(root);
    }

    private void pulse(View v, long delay) {
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setAlpha(0.9f);
        v.animate().scaleX(1.6f).scaleY(1.6f).alpha(0f).setDuration(2000).setStartDelay(delay)
                .withEndAction(() -> { if (!isFinishing()) pulse(v, 0); }).start();
    }

    private View action(String icon, String label, int color, View.OnClickListener l) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView c = Ui.circle(this, icon, color, 76, 30);
        col.addView(c);
        TextView t = Ui.text(this, label, 14, Ui.MUTED, false);
        t.setGravity(Gravity.CENTER);
        col.addView(t, Ui.lp(this, 8));
        c.setOnClickListener(l);
        col.setOnClickListener(l);
        c.setContentDescription(label);
        return col;
    }

    private void showDetails() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        root.setPadding(p, Ui.dp(this, 40), p, p);
        sv.addView(root);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView coin = new ImageView(this);
        coin.setImageResource(R.drawable.coin);
        head.addView(coin, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        TextView h = Ui.text(this, "  Pénzes Zsák", 22, Ui.TEXT, true);
        head.addView(h);
        root.addView(head);

        LinearLayout card = Ui.card(this);
        TextView body = Ui.text(this, msg.isEmpty() ? "(üres üzenet)" : msg, 17, Ui.TEXT, false);
        body.setTextIsSelectable(true);
        card.addView(body);
        root.addView(card, Ui.lp(this, 18));

        if (!click.isEmpty()) {
            Button web = Ui.button(this, "Megnyitás a weboldalon", false);
            web.setOnClickListener(v -> openWeb());
            root.addView(web, Ui.lp(this, 14));
        }
        Button close = Ui.button(this, "Bezár", true);
        close.setOnClickListener(v -> finishAndRemoveTask());
        root.addView(close, Ui.lp(this, 10));
        setContentView(sv);
    }

    private void openWeb() {
        Runnable go = () -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(click)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
        };
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km != null && km.isKeyguardLocked()) {
            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() { go.run(); }
            });
        } else {
            go.run();
        }
    }

    @Override
    public void onBackPressed() {
        if (Caller.ringing) { Caller.stop(this); }
        finishAndRemoveTask();
    }

    @SuppressWarnings("unused")
    private static int unused() { return Color.BLACK; }
}

package hu.dowellfx.penzeszsak;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Apró segédfüggvények a kódból épített felülethez (nincs XML layout, nincs külső könyvtár). */
final class Ui {
    private Ui() {}

    static final int BG = 0xFF0A0F0C, PANEL = 0xFF111A15, LINE = 0xFF22352B, TEXT = 0xFFEEF6F0,
            MUTED = 0xFF93AB9B, GOLD = 0xFFF2C14E, GREEN = 0xFF2FD889, RED = 0xFFFF5D6C;

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    static GradientDrawable round(int fill, int stroke, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(dp(c, 1), stroke);
        return g;
    }

    static Button button(Context c, String s, boolean primary) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? 0xFF2C1D00 : TEXT);
        b.setBackground(round(primary ? GOLD : 0xFF1A2A21, primary ? 0 : LINE, 12, c));
        b.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
        b.setStateListAnimator(null);
        return b;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(PANEL, LINE, 14, c));
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        return l;
    }

    static LinearLayout.LayoutParams lp(Context c, int topMarginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, topMarginDp);
        return p;
    }

    static TextView circle(Context c, String s, int fill, int sizeDp, float textSp) {
        TextView t = text(c, s, textSp, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        t.setBackground(g);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return t;
    }
}

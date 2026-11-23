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

    static final int BG = 0xFF070B09, PANEL = 0xFF0F1713, LINE = 0xFF1F3127, TEXT = 0xFFEEF6F0,
            MUTED = 0xFF8FA597, GOLD = 0xFFF2C14E, GREEN = 0xFF2FD889, RED = 0xFFFF5D6C, ORANGE = 0xFFFFA14A;

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

    /** Kerek kivágás (a hívó képéhez). */
    static android.graphics.Bitmap circle(android.graphics.Bitmap src) {
        int n = Math.min(src.getWidth(), src.getHeight());
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(out);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        cv.drawCircle(n / 2f, n / 2f, n / 2f, p);
        p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        cv.drawBitmap(src, (n - src.getWidth()) / 2f, (n - src.getHeight()) / 2f, p);
        return out;
    }

    /** A hívó képe: a választott fotó körben, vagy a DowellFX logó lekerekített négyzetben. */
    static android.widget.ImageView avatar(Context c, int sizeDp) {
        android.widget.ImageView v = new android.widget.ImageView(c);
        android.graphics.Bitmap ph = P.callerPhoto(c);
        final boolean photo = ph != null;
        if (photo) v.setImageBitmap(ph); else v.setImageResource(R.drawable.logo);
        v.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        final int r = dp(c, sizeDp) * 26 / 100;
        v.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(android.view.View view, android.graphics.Outline o) {
                if (photo) o.setOval(0, 0, view.getWidth(), view.getHeight());
                else o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    static boolean photoAvatar(Context c) { return P.callerPhoto(c) != null; }

    static TextView pill(Context c, String s, int color) {
        TextView t = text(c, s, 14, color, true);
        t.setBackground(round((color & 0x00FFFFFF) | 0x26000000, (color & 0x00FFFFFF) | 0x66000000, 99, c));
        t.setPadding(dp(c, 14), dp(c, 6), dp(c, 14), dp(c, 6));
        t.setLetterSpacing(0.04f);
        return t;
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

package id.elkhair.sholat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Animasi cuaca berlapis (gaya aplikasi cuaca): langit mengikuti jam, matahari bersinar pelan, bulan sabit,
 * bintang berkelip, awan lembut berlapis parallax, hujan dua lapis dengan percikan, kilat, dan kabut yang bergerak.
 * Sprite awan dibuat sekali; per frame hanya menggambar bitmap dan garis, ~30 fps.
 */
public class WeatherSkyView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Random rnd = new Random(11);
    private int kind = Weather.PARTLY, minute = 720, rise = 360, set = 1080;
    private Bitmap[] cloudSprites;
    private float[][] stars, clouds, rain, splash, fog;
    private Shader skyShader;
    private int skyKey = -1, w, h;
    private long flashAt = -1, nextFlash;
    private Path bolt;
    private final long t0 = SystemClock.uptimeMillis();

    public WeatherSkyView(Context c) { super(c); setLayerType(LAYER_TYPE_HARDWARE, null); }

    void set(int weatherKind, int minuteOfDay, int sunrise, int sunset) {
        kind = weatherKind; minute = minuteOfDay; rise = sunrise; set = sunset; skyKey = -1;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        w = nw; h = nh; skyKey = -1;
        stars = new float[140][4];
        for (float[] s : stars) { s[0] = rnd.nextFloat() * w; s[1] = rnd.nextFloat() * h * 0.7f; s[2] = (0.6f + rnd.nextFloat() * 1.6f) * h / 1080f; s[3] = rnd.nextFloat() * 6.28f; }
        cloudSprites = new Bitmap[] {cloudSprite(7, 0.0f), cloudSprite(9, 0.3f), cloudSprite(6, 0.6f)};
        clouds = new float[9][5];   // x, y, skala, kecepatan(px/dtk), lapisan 0..2
        for (int i = 0; i < clouds.length; i++) {
            float layer = i % 3;
            clouds[i][0] = rnd.nextFloat() * w * 1.4f - w * 0.2f;
            clouds[i][1] = h * (0.02f + 0.38f * rnd.nextFloat()) + layer * h * 0.06f;
            clouds[i][2] = (0.55f + 0.3f * layer) * (0.8f + 0.4f * rnd.nextFloat());
            clouds[i][3] = (6 + layer * 10) * w / 1920f;
            clouds[i][4] = layer;
        }
        rain = new float[260][4];   // x, y, panjang, lapisan
        for (float[] r : rain) { r[0] = rnd.nextFloat() * w; r[1] = rnd.nextFloat() * h; r[3] = rnd.nextInt(2); r[2] = (r[3] == 0 ? 0.03f : 0.06f) * h; }
        splash = new float[30][3];
        fog = new float[4][3];
        for (int i = 0; i < fog.length; i++) { fog[i][0] = rnd.nextFloat() * w; fog[i][1] = h * (0.35f + 0.15f * i); fog[i][2] = (10 + 8 * i) * w / 1920f; }
    }

    /** Awan lembut: lingkaran-lingkaran bergradasi digambar kecil lalu diperbesar (blur murah). */
    private Bitmap cloudSprite(int puffs, float seed) {
        int sw = 220, sh = 90;
        Bitmap b = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random r = new Random((long) (seed * 1000) + 3);
        for (int i = 0; i < puffs; i++) {
            float cx = sw * (0.18f + 0.64f * i / (puffs - 1f)) + r.nextFloat() * 10 - 5;
            float cy = sh * (0.62f - 0.25f * (float) Math.sin(Math.PI * i / (puffs - 1f))) + r.nextFloat() * 6;
            float rad = sh * (0.22f + 0.18f * (float) Math.sin(Math.PI * i / (puffs - 1f)) + r.nextFloat() * 0.06f);
            q.setShader(new RadialGradient(cx, cy, rad, new int[] {0xFFFFFFFF, 0xCCFFFFFF, 0x00FFFFFF}, new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, rad, q);
        }
        Bitmap small = Bitmap.createScaledBitmap(b, sw / 3, sh / 3, true);
        return Bitmap.createScaledBitmap(small, sw * 3, sh * 3, true);
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private boolean night() { return minute < rise - 20 || minute > set + 20; }

    private Shader sky() {
        int key = minute * 10 + kind;
        if (key == skyKey && skyShader != null) return skyShader;
        int nightT = 0xFF040817, nightM = 0xFF0B1630, nightB = 0xFF1A2A4E;
        int dayT = 0xFF1F64C4, dayM = 0xFF4C93DE, dayB = 0xFFA9D6F4;
        int warmT = 0xFF2E2A66, warmM = 0xFFB35E7A, warmB = 0xFFF6A35E;
        float dawn = (minute - (rise - 45)) / 90f, dusk = (minute - (set - 45)) / 90f;
        int[] a, b;
        float t;
        if (dawn >= 0 && dawn <= 1) { a = dawn < .5f ? new int[] {nightT, nightM, nightB} : new int[] {warmT, warmM, warmB}; b = dawn < .5f ? new int[] {warmT, warmM, warmB} : new int[] {dayT, dayM, dayB}; t = dawn < .5f ? dawn * 2 : (dawn - .5f) * 2; }
        else if (dusk >= 0 && dusk <= 1) { a = dusk < .5f ? new int[] {dayT, dayM, dayB} : new int[] {warmT, warmM, warmB}; b = dusk < .5f ? new int[] {warmT, warmM, warmB} : new int[] {nightT, nightM, nightB}; t = dusk < .5f ? dusk * 2 : (dusk - .5f) * 2; }
        else if (night()) { a = b = new int[] {nightT, nightM, nightB}; t = 0; }
        else { a = b = new int[] {dayT, dayM, dayB}; t = 0; }
        int[] col = new int[3];
        float grey = kind == Weather.CLOUDY ? 0.45f : kind >= Weather.DRIZZLE ? 0.62f : kind == Weather.FOG ? 0.5f : kind == Weather.PARTLY ? 0.12f : 0f;
        int greyCol = night() ? 0xFF1B1F27 : 0xFF8C98A6;
        for (int i = 0; i < 3; i++) col[i] = mix(mix(a[i], b[i], t), i == 2 && !night() ? 0xFFB7C1CC : greyCol, grey);
        skyShader = new LinearGradient(0, 0, 0, h, col, new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        skyKey = key;
        return skyShader;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (w == 0) return;
        float sec = (SystemClock.uptimeMillis() - t0) / 1000f;
        boolean night = night();
        p.setColor(0xFFFFFFFF);
        p.setShader(sky());
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // bintang berkelip
        if (night && kind <= Weather.PARTLY) {
            for (float[] s : stars) {
                float tw = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(sec * 1.3f + s[3]));
                p.setColor(((int) (tw * 220) << 24) | 0xFFFFFF);
                c.drawCircle(s[0], s[1], s[2], p);
            }
        }

        // matahari / bulan di busurnya
        float t = night ? ((minute - set + 1440) % 1440) / (float) Math.max(1, (rise - set + 1440) % 1440)
                : (minute - rise) / (float) Math.max(1, set - rise);
        t = Math.max(0, Math.min(1, t));
        float cx = w * (0.12f + 0.76f * t), cy = h * (0.7f - 0.52f * (float) Math.sin(Math.PI * t));
        float visible = kind <= Weather.PARTLY ? 1f : kind == Weather.CLOUDY ? 0.35f : 0.15f;
        if (!night) {
            float r = h * 0.055f, pulse = 1f + 0.04f * (float) Math.sin(sec * 0.8f);
            p.setColor(0xFFFFFFFF);
        p.setShader(new RadialGradient(cx, cy, r * 6 * pulse, new int[] {alpha(0xFFF7D6, 0.55f * visible), alpha(0xFFE8A8, 0.18f * visible), 0x00FFFFFF}, new float[] {0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r * 6 * pulse, p);
            p.setShader(null);
            if (kind <= Weather.PARTLY) {
                p.setColor(0xFFFFFFFF);
        p.setShader(new RadialGradient(cx, cy, r * 1.25f, new int[] {0xFFFFFDF2, 0xFFFFF1C4, 0x00FFE9A8}, new float[] {0f, 0.75f, 1f}, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, r * 1.25f, p);
                p.setShader(null);
            }
        } else if (visible > 0.3f) {
            float r = h * 0.04f;
            p.setColor(0xFFFFFFFF);
        p.setShader(new RadialGradient(cx, cy, r * 4, new int[] {alpha(0xDDE6FF, 0.35f * visible), 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r * 4, p);
            p.setShader(null);
            Path moon = new Path();
            moon.addCircle(cx, cy, r, Path.Direction.CW);
            Path cut = new Path();
            cut.addCircle(cx + r * 0.45f, cy - r * 0.2f, r * 0.9f, Path.Direction.CW);
            moon.op(cut, Path.Op.DIFFERENCE);
            p.setColor(0xFFF1F3FA);
            c.drawPath(moon, p);
        }

        // awan berlapis (belakang -> depan)
        int nClouds = kind == Weather.CLEAR ? 2 : kind == Weather.PARTLY ? 5 : clouds.length;
        float cloudAlpha = night ? 0.35f : kind >= Weather.RAIN ? 0.75f : 0.9f;
        int tint = night ? 0xFF6A7488 : kind >= Weather.DRIZZLE ? 0xFF9AA3AE : 0xFFFFFFFF;
        for (int layer = 0; layer < 3; layer++) {
            for (int i = 0; i < nClouds; i++) {
                float[] cl = clouds[i];
                if ((int) cl[4] != layer) continue;
                Bitmap sp = cloudSprites[i % 3];
                float sw = sp.getWidth() * cl[2] * w / 1920f, sh = sp.getHeight() * cl[2] * w / 1920f;
                float x = ((cl[0] + w + sec * cl[3]) % (w + sw)) - sw;
                p.setColorFilter(new android.graphics.PorterDuffColorFilter(tint, android.graphics.PorterDuff.Mode.MULTIPLY));
                p.setAlpha((int) (255 * cloudAlpha * (0.6f + 0.2f * layer)));
                c.drawBitmap(sp, null, new android.graphics.RectF(x, cl[1], x + sw, cl[1] + sh), p);
            }
        }
        p.setColorFilter(null);
        p.setAlpha(255);

        // kabut bergerak
        if (kind == Weather.FOG) {
            for (float[] f : fog) {
                float x = (f[0] + sec * f[2]) % (w * 2) - w;
                p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, f[1] - h * 0.12f, 0, f[1] + h * 0.12f, new int[] {0x00D8DEE4, 0x66D8DEE4, 0x00D8DEE4}, null, Shader.TileMode.CLAMP));
                c.drawRect(x, f[1] - h * 0.12f, x + w * 2, f[1] + h * 0.12f, p);
            }
            p.setShader(null);
        }

        // hujan dua lapis + percikan
        boolean wet = kind == Weather.DRIZZLE || kind == Weather.RAIN || kind == Weather.THUNDER;
        if (wet) {
            int n = kind == Weather.DRIZZLE ? 90 : rain.length;
            float dt = 1 / 30f, wind = 0.12f;
            p.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < n; i++) {
                float[] r = rain[i];
                float speed = (r[3] == 0 ? 0.9f : 1.6f) * h;
                r[1] += speed * dt;
                r[0] -= speed * dt * wind;
                if (r[1] > h) {
                    if (r[3] == 1 && rnd.nextInt(3) == 0) for (float[] s : splash) if (s[2] <= 0) { s[0] = r[0]; s[1] = h * (0.9f + 0.1f * rnd.nextFloat()); s[2] = 1; break; }
                    r[1] = -r[2]; r[0] = rnd.nextFloat() * w * 1.1f;
                }
                p.setStrokeWidth((r[3] == 0 ? 1.2f : 2.2f) * h / 1080f);
                p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(r[0], r[1], r[0] + r[2] * wind, r[1] + r[2], 0x00CFDDF0, r[3] == 0 ? 0x66CFDDF0 : 0xAADCE8F7, Shader.TileMode.CLAMP));
                c.drawLine(r[0], r[1], r[0] + r[2] * wind, r[1] + r[2], p);
            }
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            for (float[] s : splash) if (s[2] > 0) {
                p.setStrokeWidth(1.5f * h / 1080f);
                p.setColor(alpha(0xDCE8F7, 0.6f * s[2]));
                float rr = (1 - s[2]) * h * 0.012f + 2;
                c.drawOval(s[0] - rr * 2, s[1] - rr * 0.6f, s[0] + rr * 2, s[1] + rr * 0.6f, p);
                s[2] -= 0.08f;
            }
            p.setStyle(Paint.Style.FILL);
        }

        // kilat
        if (kind == Weather.THUNDER) {
            long now = SystemClock.uptimeMillis();
            if (nextFlash == 0) nextFlash = now + 4000;
            if (now > nextFlash) { flashAt = now; nextFlash = now + 5000 + rnd.nextInt(9000); bolt = bolt(); }
            float age = (now - flashAt) / 1000f;
            if (flashAt > 0 && age < 0.6f) {
                float k = age < 0.08f ? 1f : age < 0.16f ? 0.3f : age < 0.24f ? 0.8f : Math.max(0, 1 - age / 0.6f) * 0.5f;
                p.setColor(alpha(0xE8EEFF, 0.45f * k));
                c.drawRect(0, 0, w, h, p);
                if (age < 0.3f && bolt != null) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(3f * h / 1080f);
                    p.setColor(alpha(0xFFFFFF, k));
                    c.drawPath(bolt, p);
                    p.setStyle(Paint.Style.FILL);
                }
            }
        }
        postInvalidateDelayed(33);
    }

    private Path bolt() {
        Path b = new Path();
        float x = w * (0.2f + 0.6f * rnd.nextFloat()), y = h * 0.1f;
        b.moveTo(x, y);
        while (y < h * 0.65f) { x += (rnd.nextFloat() - 0.5f) * w * 0.05f; y += h * (0.03f + 0.04f * rnd.nextFloat()); b.lineTo(x, y); }
        return b;
    }

    private static int alpha(int rgb, float a) { return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | (rgb & 0xFFFFFF); }
}

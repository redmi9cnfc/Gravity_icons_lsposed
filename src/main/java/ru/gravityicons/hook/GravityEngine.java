package ru.gravityicons.hook;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Choreographer;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Быстрый и реалистичный 2D-физический движок для иконок, папок и виджетов лаунчера (v1.8).
 * <p>
 * Применяет setTranslationX, setTranslationY и setRotation напрямую к View,
 * благодаря чему визуальное положение И тач-клики по движущимся и упавшим иконкам
 * работают идеально прямо в их новом физическом месте на экране!
 */
public final class GravityEngine implements SensorEventListener {

    private static final String TAG = "GravityIcons";

    /** Состояние одного элемента (иконка, папка, виджет). */
    public static final class Body {
        public float x, y;          // смещение от layout-позиции, px
        public float vx, vy;        // скорость, px/с
        public float angle;         // угол визуального наклона, градусов
        public float mass = 1f;     // масса (зависит от размера объекта)
        public boolean awake = true;
        public boolean touching;   // контакт с другим элементом в этом кадре
        public boolean pinnedMinX, pinnedMaxX, pinnedMinY, pinnedMaxY;
        View view;
    }

    // ===================== НАСТРОЙКИ УСКОРЕННОЙ ФИЗИКИ =====================
    private static final float GRAVITY_PX = 4800f;       // Быстрое динамичное падение
    private static final float DAMPING = 0.35f;          // Меньше сопротивления воздуха -> выше скорость
    private static final float WALL_BOUNCE = 0.52f;       // Упругие динамичные отскоки от стен
    private static final float WALL_FRICTION = 0.70f;
    private static final float ICON_BOUNCE = 0.45f;       // Эластичные отскоки иконок друг от друга
    private static final float MAX_SPEED = 6000f;         // Высокий предел скорости
    private static final float SLEEP_SPEED = 8f;
    private static final float WAKE_ACCEL = 0.38f;
    private static final float FILTER_ALPHA = 0.28f;
    private static final float RADIUS_FACTOR = 0.42f;

    /** Порог сильной тряски для вкл/выкл гравитации (повышен до 32.0 м/с²). */
    private static final float TOGGLE_SHAKE_THRESHOLD = 32.0f;
    /** Порог обычной тряски для импульса элементам (м/с²). */
    private static final float SHAKE_THRESHOLD = 2.4f;
    /** Минимальный интервал между переключениями (мс). */
    private static final long TOGGLE_DEBOUNCE_MS = 1200L;
    /** Сила толчка от встряски. */
    private static final float IMPULSE_GAIN_PX = 600f;
    // =====================================================================

    private static final float WAKE_THRESHOLD_PX = WAKE_ACCEL / 9.81f * GRAVITY_PX;

    private static final GravityEngine INSTANCE = new GravityEngine();

    public static GravityEngine get() {
        return INSTANCE;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread sensorThread = new HandlerThread("gravity-sensors");
    private final Map<View, Body> bodies = new ConcurrentHashMap<>();
    private final Map<Class<?>, Boolean> iconClassCache = new ConcurrentHashMap<>();

    private SensorManager sensorManager;
    private Context appContext;
    private Activity mainActivity;

    private volatile boolean attached;
    private volatile boolean screenOn = true;
    private volatile boolean launcherInForeground = false; // Видим ли рабочий стол в данный момент
    /** Флаг активности гравитации. Изначально false (элементы на месте). */
    private volatile boolean gravityEnabled = false;
    private long lastToggleTime = 0L;

    private volatile float accelX;
    private volatile float accelY;
    private volatile float impulseX;
    private volatile float impulseY;
    private volatile float asleepPlaneAccel;

    private boolean callbackRunning;
    private long lastFrameNanos;

    private float lastGx, lastGy, lastGz;
    private boolean hasGravitySample;

    private GravityEngine() {}

    public boolean isAttached() {
        return attached;
    }

    public synchronized void attach(Activity activity) {
        if (attached) return;
        attached = true;
        mainActivity = activity;
        appContext = activity.getApplicationContext();
        Log.i(TAG, "engine attached for " + appContext.getPackageName());

        sensorThread.start();
        Handler sensorHandler = new Handler(sensorThread.getLooper());
        sensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            Sensor gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            Sensor acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            Sensor linear = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

            if (gravity != null) {
                sensorManager.registerListener(this, gravity, SensorManager.SENSOR_DELAY_GAME, sensorHandler);
            } else if (acc != null) {
                sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME, sensorHandler);
            }
            if (linear != null) {
                sensorManager.registerListener(this, linear, SensorManager.SENSOR_DELAY_GAME, sensorHandler);
            } else if (acc != null && gravity != null) {
                sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME, sensorHandler);
            }
        }

        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                appContext.registerReceiver(screenReceiver, filter);
            }
        } catch (Throwable t) {
            Log.w(TAG, "failed to register screenReceiver: " + t);
        }

        startCallback();
    }

    public void onLauncherResumed(Activity activity) {
        mainActivity = activity;
        launcherInForeground = true;
        if (gravityEnabled) {
            wakeAll();
        }
    }

    public void onLauncherPaused(Activity activity) {
        launcherInForeground = false;
    }

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            screenOn = Intent.ACTION_SCREEN_ON.equals(intent.getAction());
        }
    };

    // -------------------- доступ из отрисовки и тач-событий --------------------

    public Body getBodyIfIcon(View v) {
        if (v == null || v.getWidth() <= 0 || v.getHeight() <= 0) return null;
        if (!isIconClass(v.getClass())) return null;
        if (isExcluded(v)) return null;
        Body b = bodies.get(v);
        if (b == null) {
            b = new Body();
            b.mass = Math.max(0.8f, (v.getWidth() * v.getHeight()) / 22000f);
            bodies.put(v, b);
            Log.i(TAG, "element registered: " + v.getClass().getName() + " (mass=" + b.mass + ")");
            if (gravityEnabled) {
                wakeAll();
            }
        }
        return b;
    }

    /**
     * Элементами физики являются иконки приложений, папки и виджеты рабочего стола.
     */
    private boolean isIconClass(Class<?> c) {
        Boolean cached = iconClassCache.get(c);
        if (cached != null) return cached;
        boolean ok = false;
        Class<?> k = c;
        for (int i = 0; i < 12 && k != null; i++) {
            String name = k.getName();
            String simpleName = k.getSimpleName();
            if (simpleName.contains("BubbleTextView")
                    || simpleName.contains("AppIcon")
                    || simpleName.contains("FolderIcon")
                    || simpleName.contains("AppWidgetHostView")
                    || simpleName.contains("WidgetHostView")
                    || simpleName.contains("QsbContainer")
                    || name.contains("BubbleTextView")
                    || name.contains("FolderIcon")
                    || name.contains("AppWidgetHostView")) {
                ok = true;
                break;
            }
            k = k.getSuperclass();
        }
        iconClassCache.put(c, ok);
        return ok;
    }

    /**
     * Исключаем элементы внутри открытых папок, меню приложений и внутренности FolderIcon.
     */
    private static boolean isExcluded(View v) {
        if (v == null) return true;

        // Элементы ВНУТРИ FolderIcon (превью иконки папки) НЕ должны двигаться отдельно
        android.view.ViewParent directParent = v.getParent();
        if (directParent != null) {
            String pSimpleName = directParent.getClass().getSimpleName();
            if (pSimpleName.contains("FolderIcon") || pSimpleName.contains("Folder")) {
                if (INSTANCE.isIconClass(directParent.getClass())) {
                    return true;
                }
            }
        }

        android.view.ViewParent p = directParent;
        int hops = 0;
        while (p instanceof ViewGroup && hops < 6) {
            String simpleName = p.getClass().getSimpleName().toLowerCase(java.util.Locale.US);
            if (simpleName.contains("allapps")
                    || simpleName.contains("popup")
                    || simpleName.contains("deepshortcut")
                    || simpleName.equals("folder")
                    || simpleName.equals("folderpagedview")
                    || simpleName.contains("folderview")) {
                return true;
            }
            p = p.getParent();
            hops++;
        }
        return false;
    }

    // -------------------- датчики и встряска --------------------

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {}

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Если пользователь находится НЕ на рабочем столе (в другом приложении/игре) — датчики игнорируются!
        if (!launcherInForeground) return;
        if (event == null || event.values == null || event.values.length < 2 || event.sensor == null) return;
        int type = event.sensor.getType();

        float v0 = event.values[0];
        float v1 = event.values[1];
        float v2 = event.values.length > 2 ? event.values[2] : 0f;

        if (type == Sensor.TYPE_LINEAR_ACCELERATION) {
            float mag = (float) Math.hypot(Math.hypot(v0, v1), v2);
            checkToggleShake(mag);

            if (gravityEnabled && mag > SHAKE_THRESHOLD) {
                float[] s = {0f, 0f};
                toScreen(v0, v1, rotation(), s);
                impulseX += (-s[0] * IMPULSE_GAIN_PX - impulseX) * 0.55f;
                impulseY += (-s[1] * IMPULSE_GAIN_PX - impulseY) * 0.55f;
                wakeAll();
            }
            return;
        }

        if (type == Sensor.TYPE_ACCELEROMETER) {
            if (hasGravitySample) {
                float shakeMag = (float) Math.hypot(Math.hypot(v0 - lastGx, v1 - lastGy), v2 - lastGz);
                checkToggleShake(shakeMag);

                if (gravityEnabled && shakeMag > SHAKE_THRESHOLD) {
                    float[] s = {0f, 0f};
                    toScreen(v0 - lastGx, v1 - lastGy, rotation(), s);
                    impulseX += (-s[0] * IMPULSE_GAIN_PX - impulseX) * 0.55f;
                    impulseY += (-s[1] * IMPULSE_GAIN_PX - impulseY) * 0.55f;
                    wakeAll();
                }
            }
            if (!hasGravitySample) {
                lastGx = v0;
                lastGy = v1;
                lastGz = v2;
                hasGravitySample = true;
            }
            final float a = 0.85f;
            lastGx = a * lastGx + (1f - a) * v0;
            lastGy = a * lastGy + (1f - a) * v1;
            lastGz = a * lastGz + (1f - a) * v2;
            v0 = lastGx;
            v1 = lastGy;
            v2 = lastGz;
        } else if (type == Sensor.TYPE_GRAVITY) {
            lastGx = v0;
            lastGy = v1;
            lastGz = v2;
            hasGravitySample = true;
        } else {
            return;
        }

        if (!gravityEnabled) {
            return;
        }

        float[] g = {0f, 0f};
        toScreen(v0, v1, rotation(), g);

        float targetX = g[0] / 9.81f * GRAVITY_PX;
        float targetY = g[1] / 9.81f * GRAVITY_PX;

        float newAx = accelX + FILTER_ALPHA * (targetX - accelX);
        float newAy = accelY + FILTER_ALPHA * (targetY - accelY);

        float mag = (float) Math.hypot(newAx, newAy);
        float deltaMag = Math.abs(mag - asleepPlaneAccel);
        float deltaStep = (float) Math.hypot(newAx - accelX, newAy - accelY);

        accelX = newAx;
        accelY = newAy;

        if (deltaMag > WAKE_THRESHOLD_PX || deltaStep > WAKE_THRESHOLD_PX * 0.5f) {
            wakeAll();
        } else {
            ensureCallback();
        }
    }

    private void checkToggleShake(float shakeMag) {
        if (!launcherInForeground) return;
        if (shakeMag > TOGGLE_SHAKE_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastToggleTime > TOGGLE_DEBOUNCE_MS) {
                lastToggleTime = now;
                gravityEnabled = !gravityEnabled;
                Log.i(TAG, "VERY Strong shake detected! Gravity enabled = " + gravityEnabled);
                wakeAll();
            }
        }
    }

    private static void toScreen(float v0, float v1, int rot, float[] out) {
        switch (rot) {
            case Surface.ROTATION_90:  out[0] = v1;  out[1] = v0;  break;
            case Surface.ROTATION_180: out[0] = v0;  out[1] = -v1; break;
            case Surface.ROTATION_270: out[0] = -v1; out[1] = -v0; break;
            default:                   out[0] = -v0; out[1] = v1;  break;
        }
    }

    private int rotation() {
        Display d = appDisplay();
        return d == null ? Surface.ROTATION_0 : d.getRotation();
    }

    @SuppressLint("deprecation")
    private Display appDisplay() {
        if (mainActivity != null && !mainActivity.isDestroyed() && !mainActivity.isFinishing()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    return mainActivity.getDisplay();
                } else {
                    return mainActivity.getWindowManager().getDefaultDisplay();
                }
            } catch (Throwable ignored) {}
        }
        if (appContext != null) {
            try {
                WindowManager wm = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null) return wm.getDefaultDisplay();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private void wakeAll() {
        for (Body b : bodies.values()) {
            b.awake = true;
        }
        ensureCallback();
    }

    // -------------------- игровой цикл (vsync) --------------------

    private void ensureCallback() {
        main.post(requestCallback);
    }

    private final Runnable requestCallback = new Runnable() {
        @Override
        public void run() {
            if (callbackRunning || !attached || !screenOn || !launcherInForeground) return;
            callbackRunning = true;
            Choreographer.getInstance().postFrameCallback(frameCallback);
        }
    };

    private void startCallback() {
        lastFrameNanos = 0L;
        ensureCallback();
    }

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            callbackRunning = false;
            if (tick(frameTimeNanos) && screenOn && launcherInForeground) {
                callbackRunning = true;
                Choreographer.getInstance().postFrameCallback(this);
            }
        }
    };

    private boolean tick(long nowNanos) {
        if (!launcherInForeground) return false;

        float dt = lastFrameNanos == 0L
                ? 0.016f
                : (nowNanos - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = nowNanos;
        if (dt <= 0f) dt = 0.016f;
        if (dt > 0.05f) dt = 0.05f;

        // Если гравитация отключена — плавно возвращаем элементы на исходные места (x=0, y=0, angle=0)
        if (!gravityEnabled) {
            boolean returning = false;
            for (Body b : bodies.values()) {
                if (b.x != 0f || b.y != 0f || b.angle != 0f) {
                    b.x += (0f - b.x) * 0.22f;
                    b.y += (0f - b.y) * 0.22f;
                    b.angle += (0f - b.angle) * 0.22f;
                    if (Math.abs(b.x) < 0.5f) b.x = 0f;
                    if (Math.abs(b.y) < 0.5f) b.y = 0f;
                    if (Math.abs(b.angle) < 0.1f) b.angle = 0f;
                    b.vx = 0f;
                    b.vy = 0f;
                    if (b.view != null) {
                        b.view.setTranslationX(b.x);
                        b.view.setTranslationY(b.y);
                        b.view.setRotation(b.angle);
                    }
                    returning = true;
                } else {
                    b.vx = 0f;
                    b.vy = 0f;
                    b.angle = 0f;
                    b.awake = false;
                    if (b.view != null) {
                        b.view.setTranslationX(0f);
                        b.view.setTranslationY(0f);
                        b.view.setRotation(0f);
                    }
                }
            }
            return returning;
        }

        final float planeAccel = (float) Math.hypot(accelX, accelY);
        final float ix = impulseX;
        final float iy = impulseY;

        List<Body> active = new ArrayList<>(bodies.size());
        Iterator<Map.Entry<View, Body>> it = bodies.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<View, Body> e = it.next();
            View v = e.getKey();
            Body b = e.getValue();
            if (!isAlive(v)) {
                it.remove();
                continue;
            }
            b.view = v;
            b.touching = false;
            active.add(b);
        }
        if (active.isEmpty()) {
            return false;
        }

        // Интегрирование физики с учётом массы и затухания
        for (Body b : active) {
            if (!b.awake) continue;
            float invMass = 1f / b.mass;
            b.vx += (accelX + ix) * invMass * dt;
            b.vy += (accelY + iy) * invMass * dt;
            float damp = Math.max(0f, 1f - DAMPING * dt);
            b.vx *= damp;
            b.vy *= damp;
            float sp = (float) Math.hypot(b.vx, b.vy);
            if (sp > MAX_SPEED) {
                float k = MAX_SPEED / sp;
                b.vx *= k;
                b.vy *= k;
            }
            b.x += b.vx * dt;
            b.y += b.vy * dt;

            // Динамический визуальный наклон при движении
            float targetAngle = Math.max(-14f, Math.min(14f, b.vx * 0.012f));
            b.angle += (targetAngle - b.angle) * Math.min(1f, dt * 10f);

            clampToParent(b);
        }

        impulseX *= 0.82f;
        impulseY *= 0.82f;

        // Две итерации решения упругих столкновений
        for (int iter = 0; iter < 2; iter++) {
            for (int i = 0; i < active.size(); i++) {
                Body a = active.get(i);
                if (!(a.view.getParent() instanceof View)) continue;
                View pa = (View) a.view.getParent();
                for (int j = i + 1; j < active.size(); j++) {
                    Body c = active.get(j);
                    if (!a.awake && !c.awake) continue;
                    if (c.view.getParent() != pa) continue;
                    collide(a, c);
                }
            }
        }

        boolean needNextFrame = false;
        for (Body b : active) {
            if (!b.awake) continue;
            clampToParent(b);

            // Нативно обновляем физическое положение И тач-зону элемента
            if (b.view != null) {
                b.view.setTranslationX(b.x);
                b.view.setTranslationY(b.y);
                b.view.setRotation(b.angle);
            }

            float sp = (float) Math.hypot(b.vx, b.vy);
            boolean supported = b.pinnedMinX || b.pinnedMaxX || b.pinnedMinY || b.pinnedMaxY
                    || b.touching || planeAccel < 90f;
            if (sp < SLEEP_SPEED && supported) {
                b.vx = 0f;
                b.vy = 0f;
                b.awake = false;
            } else {
                needNextFrame = true;
            }
        }

        if (!needNextFrame) {
            asleepPlaneAccel = planeAccel;
        }
        return needNextFrame;
    }

    private static boolean isAlive(View v) {
        return v.isAttachedToWindow()
                && v.getParent() instanceof View
                && v.getVisibility() == View.VISIBLE;
    }

    private void clampToParent(Body b) {
        View v = b.view;
        if (v == null || !(v.getParent() instanceof ViewGroup)) return;
        ViewGroup p = (ViewGroup) v.getParent();

        float minX = -v.getLeft();
        float maxX = p.getWidth() - v.getLeft() - v.getWidth();
        float minY = -v.getTop();
        float maxY = p.getHeight() - v.getTop() - v.getHeight();

        b.pinnedMinX = b.pinnedMaxX = b.pinnedMinY = b.pinnedMaxY = false;

        if (b.x < minX) {
            b.x = minX;
            if (b.vx < 0f) {
                b.vx = -b.vx * WALL_BOUNCE;
                b.vy *= WALL_FRICTION;
            }
            b.pinnedMinX = true;
        } else if (b.x > maxX) {
            b.x = maxX;
            if (b.vx > 0f) {
                b.vx = -b.vx * WALL_BOUNCE;
                b.vy *= WALL_FRICTION;
            }
            b.pinnedMaxX = true;
        }
        if (b.y < minY) {
            b.y = minY;
            if (b.vy < 0f) {
                b.vy = -b.vy * WALL_BOUNCE;
                b.vx *= WALL_FRICTION;
            }
            b.pinnedMinY = true;
        } else if (b.y > maxY) {
            b.y = maxY;
            if (b.vy > 0f) {
                b.vy = -b.vy * WALL_BOUNCE;
                b.vx *= WALL_FRICTION;
            }
            b.pinnedMaxY = true;
        }
    }

    /**
     * Реалистичное столкновение твердых тел с учётом распределения массы (mass ratio).
     */
    private void collide(Body a, Body b) {
        View va = a.view;
        View vb = b.view;
        if (va == null || vb == null) return;

        float aCx = va.getLeft() + a.x + va.getWidth() * 0.5f;
        float aCy = va.getTop() + a.y + va.getHeight() * 0.5f;
        float bCx = vb.getLeft() + b.x + vb.getWidth() * 0.5f;
        float bCy = vb.getTop() + b.y + vb.getHeight() * 0.5f;

        float ra = Math.min(va.getWidth(), va.getHeight()) * RADIUS_FACTOR;
        float rb = Math.min(vb.getWidth(), vb.getHeight()) * RADIUS_FACTOR;

        float dx = bCx - aCx;
        float dy = bCy - aCy;
        float dist = (float) Math.hypot(dx, dy);
        float minDist = ra + rb;
        if (dist >= minDist) return;

        float nx, ny;
        if (dist < 0.001f) {
            nx = 0f;
            ny = -1f;
            dist = 0.001f;
        } else {
            nx = dx / dist;
            ny = dy / dist;
        }

        float overlap = minDist - dist;
        float totalMass = a.mass + b.mass;
        float wA = b.mass / totalMass; // Тяжелый объект сдвигается меньше
        float wB = a.mass / totalMass;

        a.x -= nx * overlap * wA;
        a.y -= ny * overlap * wA;
        b.x += nx * overlap * wB;
        b.y += ny * overlap * wB;

        float rvx = b.vx - a.vx;
        float rvy = b.vy - a.vy;
        float velN = rvx * nx + rvy * ny;
        if (velN < 0f) {
            float j = -(1f + ICON_BOUNCE) * velN;
            a.vx -= (j * wA) * nx;
            a.vy -= (j * wA) * ny;
            b.vx += (j * wB) * nx;
            b.vy += (j * wB) * ny;
        }

        a.touching = true;
        b.touching = true;
        a.awake = true;
        b.awake = true;
    }
}

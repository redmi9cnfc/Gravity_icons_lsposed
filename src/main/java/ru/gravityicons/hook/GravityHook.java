package ru.gravityicons.hook;

import android.app.Activity;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Универсальная точка входа LSPosed-модуля.
 * Поддерживает Tecno, Infinix, Xiaomi/Redmi/POCO, Samsung, Realme/Oppo/OnePlus,
 * Vivo, Huawei, Motorola, Pixel Launcher, Evolution X, Lawnchair, Nova и многие другие.
 */
public final class GravityHook implements IXposedHookLoadPackage {

    private static final Set<String> KNOWN_LAUNCHERS = new HashSet<>(Arrays.asList(
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "org.lineageos.trebuchet",
            "com.evolution.launcher",
            "com.evolution.launcher3",
            "com.android.launcher",
            "com.miui.home",
            "com.mi.android.globallauncher",
            "com.sec.android.app.launcher",
            "com.oppo.launcher",
            "com.oneplus.launcher",
            "com.transsion.hilauncher",
            "com.transsion.XOSLauncher",
            "com.transsion.launcher",
            "com.huawei.android.launcher",
            "com.hihonor.launcher",
            "com.bbk.launcher2",
            "com.vivo.launcher",
            "com.motorola.launcher3",
            "com.nothing.launcher",
            "lawnchair.app",
            "ch.deletescape.lawnchair.ci",
            "com.teslacoilsw.launcher",
            "ginlemon.flowerfree",
            "bitpit.launcher"
    ));

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam.packageName == null) return;

        if (!isTargetLauncherPackage(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("[GravityIcons] Loading in launcher package: " + lpparam.packageName + " (process: " + lpparam.processName + ")");

        hookLauncherLifecycle(lpparam);
        hookViewDraw(lpparam.classLoader);
    }

    private static boolean isTargetLauncherPackage(String pkgName) {
        if (KNOWN_LAUNCHERS.contains(pkgName)) return true;
        String lower = pkgName.toLowerCase();
        return lower.contains("launcher")
                || lower.contains("trebuchet")
                || lower.contains("home")
                || lower.contains("hilauncher")
                || lower.contains("xos");
    }

    /** Запускаем и отслеживаем жизненный цикл активности лаунчера. */
    private void hookLauncherLifecycle(XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;
        String[] candidateClasses = {
                "com.android.launcher3.Launcher",
                "com.android.launcher3.uioverrides.QuickstepLauncher",
                "com.google.android.apps.nexuslauncher.NexusLauncherActivity",
                "com.evolution.launcher.Launcher",
                "com.evolution.launcher3.Launcher",
                "com.miui.home.launcher.Launcher",
                "com.sec.android.app.launcher.Launcher",
                "com.sec.android.app.launcher.activity.LauncherActivity",
                "com.oppo.launcher.Launcher",
                "com.oneplus.launcher.Launcher",
                "com.transsion.hilauncher.Launcher",
                "com.transsion.XOSLauncher.Launcher",
                "com.teslacoilsw.launcher.NovaLauncher"
        };

        XC_MethodHook lifecycleHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Activity act = (Activity) param.thisObject;
                    String name = param.method.getName();
                    if ("onCreate".equals(name)) {
                        GravityEngine.get().attach(act);
                        GravityEngine.get().onLauncherResumed(act);
                    } else if ("onResume".equals(name)) {
                        GravityEngine.get().onLauncherResumed(act);
                    } else if ("onPause".equals(name) || "onStop".equals(name)) {
                        GravityEngine.get().onLauncherPaused(act);
                    }
                } catch (Throwable t) {
                    XposedBridge.log("[GravityIcons] Lifecycle callback error: " + t);
                }
            }
        };

        boolean hookedAny = false;
        for (String className : candidateClasses) {
            try {
                Class<?> cls = XposedHelpers.findClass(className, cl);
                XposedHelpers.findAndHookMethod(cls, "onCreate", Bundle.class, lifecycleHook);
                try { XposedHelpers.findAndHookMethod(cls, "onResume", lifecycleHook); } catch (Throwable ignored) {}
                try { XposedHelpers.findAndHookMethod(cls, "onPause", lifecycleHook); } catch (Throwable ignored) {}
                try { XposedHelpers.findAndHookMethod(cls, "onStop", lifecycleHook); } catch (Throwable ignored) {}
                XposedBridge.log("[GravityIcons] Hooked lifecycle in " + className);
                hookedAny = true;
            } catch (Throwable ignored) {
            }
        }

        if (!hookedAny) {
            try {
                Class<?> actClass = XposedHelpers.findClass("android.app.Activity", cl);
                XposedHelpers.findAndHookMethod(actClass, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Activity act = (Activity) param.thisObject;
                            String clsName = act.getClass().getName().toLowerCase();
                            if (clsName.contains("launcher")
                                    || clsName.contains("trebuchet")
                                    || clsName.contains("home")
                                    || clsName.contains("hilauncher")
                                    || clsName.contains("xos")
                                    || !GravityEngine.get().isAttached()) {
                                GravityEngine.get().attach(act);
                                GravityEngine.get().onLauncherResumed(act);
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("[GravityIcons] Fallback Activity.onCreate attach failed: " + t);
                        }
                    }
                });

                XposedHelpers.findAndHookMethod(actClass, "onResume", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Activity act = (Activity) param.thisObject;
                            if (GravityEngine.get().isAttached()) {
                                GravityEngine.get().onLauncherResumed(act);
                            }
                        } catch (Throwable ignored) {}
                    }
                });

                XposedHelpers.findAndHookMethod(actClass, "onPause", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Activity act = (Activity) param.thisObject;
                            if (GravityEngine.get().isAttached()) {
                                GravityEngine.get().onLauncherPaused(act);
                            }
                        } catch (Throwable ignored) {}
                    }
                });

                XposedBridge.log("[GravityIcons] Hooked fallback android.app.Activity lifecycle");
            } catch (Throwable t) {
                XposedBridge.log("[GravityIcons] Fallback Activity lifecycle hook failed: " + t);
            }
        }
    }

    /**
     * Регистрируем все элементы рабочего стола (иконки, папки, виджеты) при отрисовке.
     */
    private void hookViewDraw(ClassLoader cl) {
        try {
            Class<?> viewCls = XposedHelpers.findClass("android.view.View", cl);

            XC_MethodHook drawHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        View v = (View) param.thisObject;
                        GravityEngine.Body b = GravityEngine.get().getBodyIfIcon(v);
                        if (b != null) {
                            v.setTranslationX(b.x);
                            v.setTranslationY(b.y);
                            if (b.angle != 0f || v.getRotation() != 0f) {
                                v.setRotation(b.angle);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            };

            try {
                XposedHelpers.findAndHookMethod(viewCls, "draw", Canvas.class, drawHook);
                XposedBridge.log("[GravityIcons] Hooked android.view.View.draw(Canvas)");
            } catch (Throwable t) {
                XposedBridge.log("[GravityIcons] View.draw(Canvas) hook failed: " + t);
            }

            try {
                XposedHelpers.findAndHookMethod(viewCls, "draw", Canvas.class, ViewGroup.class, long.class, drawHook);
                XposedBridge.log("[GravityIcons] Hooked android.view.View.draw(Canvas, ViewGroup, long)");
            } catch (Throwable t) {
                XposedBridge.log("[GravityIcons] View.draw(Canvas, ViewGroup, long) hook failed: " + t);
            }

        } catch (Throwable t) {
            XposedBridge.log("[GravityIcons] View draw hook failed: " + t);
        }
    }
}

# GravityIcons (LSPosed Module)

**GravityIcons** is a high-performance, realistic 2D gravity physics module for Android launchers built for LSPosed (Zygisk). It turns your static home screen into an interactive physical sandbox where app icons, folders, and desktop widgets fall, collide, bounce, and tilt according to real-world physics!
An LSPosed module that recreates the concept of the Gravitational (Havoc) tweak: home screen icons obey gravity—falling to the bottom edge, piling up, bouncing off one another, and jumping around when the phone is shaken.

Built from scratch without using any of the original tweak's code: features a physics engine (VSync-based frame integration, circle collisions, damping, sleep/wake states) and hooks for icon rendering in Launcher3 forks.
![Android 8-16](https://img.shields.io/badge/Android-8.0_--_16-brightgreen.svg)
![LSPosed Module](https://img.shields.io/badge/LSPosed-Zygisk_Module-blue.svg)
![License](https://img.shields.io/badge/License-MIT-orange.svg)

---

## ? Features

- **Realistic 2D Physics Engine**:
  - **Gravity & Inertia**: Icons, folders, and widgets fall dynamically based on device orientation (using gravity & accelerometer sensors).
  - **Mass Distribution**: Larger widgets and folders have higher mass and momentum, pushing smaller app icons out of the way naturally.
  - **Dynamic Visual Tilt**: Objects tilt smoothly into their direction of movement (up to �14�) as they slide, roll, and bounce.
  - **Elastic Collisions**: Accurate circle & rigid-body radial collision resolution with realistic bounce restitution and wall friction.

- **Fully Interactive Touch Targets**:
  - Tapping on moving or fallen icons directly opens the app at its new physical location on the screen!
  - Works natively with Android's `View` transformation matrices.

- **Smart Wrist Shake Toggle**:
  - **Initially Fixed**: Your home screen starts in its clean, default grid layout.
  - **Toggle On/Off**: Perform a strong intentional shake of your phone (32 m/s� threshold) to trigger gravity physics.
  - **Smooth Home Return**: Shake your phone again, and all icons smoothly slide back to their exact original grid spots.

- **Universal Launcher & Custom ROM Support**:
  - Compatible with **Launcher3**, **Pixel Launcher**, **Evolution X**, **Trebuchet** (LineageOS), **Lawnchair**, **Nova Launcher**, **Nothing OS**, **Motorola**, **Samsung One UI**, **Xiaomi / Redmi / POCO (MIUI / HyperOS)**, **Tecno (HiOS)**, **Infinix (XOS)**, **Realme / OPPO / OnePlus (ColorOS)**, **Vivo / iQOO**, **Huawei / Honor**, and more!

- **Android 16 & Modern ART Optimization**:
  - Built with modern Android 14?16 hardware acceleration (`RecordingCanvas` & `RenderNode`) and lifecycle awareness.

- **Zero Background Battery & CPU Overhead**:
  - Sensor listeners and vsync frame loops automatically **freeze** when you exit the Home Screen (e.g. playing games, browsing in Chrome, or using Telegram). Gravity physics only runs when you are actively on the Home Screen.

---

## ? Requirements

- **Android Version**: Android 8.0 (API 26) up to **Android 16 (API 36)**
- **Root**: Magisk / KernelSU / APatch with Zygisk enabled
- **Xposed Framework**: **LSPosed** (or LSPosed-JingMatrix / Zygisk Next)

---

## ? Installation & Setup

1. **Download APK**: Download `app-release-unsigned.apk` (or build the project in Android Studio).
2. **Install**: Install the APK on your Android device.
3. **Enable in LSPosed**:
   - Open **LSPosed Manager**.
   - Navigate to the **Modules** tab and enable **GravityIcons**.
   - Make sure your system Launcher (e.g., *Launcher3*, *Evolution Launcher*, *MIUI Home*, *One UI Home*, *HiOS*, *XOS*, etc.) is checked in the module **Scope** list.
4. **Reboot / Restart Launcher**:
   - Force close your Launcher app or reboot your device.
5. **Enjoy**:
   - Go to your Home Screen and perform a strong wrist shake to activate gravity!

---

## ?? How It Works

1. **LSPosed Hooks**:
   - Hooks Activity lifecycle methods (`onCreate`, `onResume`, `onPause`, `onStop`) in the target launcher process to manage foreground state and sensor callbacks.
   - Hooks `android.view.View.draw` to discover and register workspace elements (`BubbleTextView`, `FolderIcon`, `AppWidgetHostView`, `QsbContainer`) into the physics simulation map.

2. **Native View Matrix Translation**:
   - Updates `setTranslationX`, `setTranslationY`, and `setRotation` directly on `View` objects on vsync frame callbacks (`Choreographer`).
   - Android's framework automatically handles both rendering translation and touch input hit-testing dispatch.

---

## ⚠️ Doesn’t work? (diagnostics)

**Main test:** after enabling the module and restarting the launcher, the icons should
**immediately fall down** — even before any shaking occurs. Let’s break it down by situation:

**A. The icons don’t even fall** — the hooks aren’t working. Check the logs:
```
adb logcat -s GravityIcons XposedBridge
```
(or LSPosed Manager → Logs, search for “GravityIcons”)

What should be in the logs when loading the launcher:
- `hooked android.view.View.draw`
- `engine attached for <launcher package>`
- `icon registered: com.android.launcher3.BubbleTextView`


What to check if there are no lines or they are missing:
1. Is the module included in LSPosed and marked as scope? Reboot after enabling.
2. Does scope point to **your** launcher? Find out the package:
   `adb shell cmd shortcut get-default-launcher`
 If it’s not on the list (Lawnchair, Nova, etc.), the module doesn’t touch it:
   add the package to `LAUNCHER_PACKAGES` (GravityHook.java) and to
   `xposed_scope` (arrays.xml), rebuild.
3. Are the icons not from the Launcher3 world? Nova/Lawnchair have their own classes — hook.
   `View.draw` will work, but `isIconClass()` won’t recognize them. Add the names
 of their view classes to `isIconClass()`.
4. There was a bug in v1.0: icon hooks weren’t found silently (findAndHookMethod searches
 for the method only in the class itself). If you have v1.0, update to v1.1.

**B. Icons fall, but shaking doesn’t wake them up** — this is a sensor/threshold issue:
- Shake **sharper** or reduce the `SHAKE_THRESHOLD` (default is 1.8 m/s2).
- The linear acceleration sensor may be missing — then it is used
  the high-frequency part of the accelerometer, it is less sensitive; reduce
  the threshold is up to ~1.2.
- Increase the `IMPULSE_GAIN_PX` if the icons are awake but barely twitching.

**C. Everything is included, but the launcher crashes/is glitchy** — check the log for crashes
(`adb logcat -b crash`), disable the module, and write down which line it crashed on.

---

## ? License

Distributed under the **GPL-3.0 License**.

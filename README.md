# 🌌 GravityIcons

**GravityIcons** is a high-performance, realistic 2D gravity physics module for Android launchers built for **LSPosed (Zygisk)**.

Turn your static home screen into a physical playground where app icons, folders, and widgets **fall, collide, bounce, stack, and tilt** according to realistic physics.

Built from scratch without using any code from the original Gravitational/Havoc tweak.

![Android 8-16](https://img.shields.io/badge/Android-8.0--16-brightgreen.svg)
![LSPosed Module](https://img.shields.io/badge/LSPosed-Zygisk-blue.svg)
![License](https://img.shields.io/badge/License-GPL--3.0-orange.svg)

---

## ✨ Features

### ⚙️ Realistic 2D Physics

* **Gravity & Inertia** — Icons, folders, and widgets react to device orientation using gravity and accelerometer sensors.
* **Mass Distribution** — Larger widgets and folders have greater mass and momentum, naturally pushing smaller icons.
* **Dynamic Tilt** — Objects smoothly tilt in the direction of movement.
* **Elastic Collisions** — Icons collide with each other and the screen edges with realistic bounce and friction.
* **Sleep / Wake States** — Objects that stop moving automatically sleep to reduce processing overhead.

### 👆 Fully Interactive Touch

* Tap moving or fallen icons to open their apps.
* Folders and widgets remain interactive.
* Uses Android's native `View` transformation system.

### 🫨 Shake to Toggle

The home screen initially stays in its normal grid layout.

Shake your phone to activate gravity:

**Icons fall, collide, bounce, and pile up.**

Shake again and the icons smoothly return to their original grid positions.

### 📱 Launcher Support

Designed for:

* Launcher3
* Pixel Launcher
* Evolution X
* Trebuchet
* Lawnchair
* Nova Launcher
* Nothing OS
* Samsung One UI
* Xiaomi / Redmi / POCO
* Motorola
* Tecno HiOS
* Infinix XOS
* Realme / OPPO / OnePlus
* Vivo / iQOO
* Huawei / Honor
* Other Launcher3-based launchers

> Compatibility depends on the launcher implementation and its internal view classes.

### ⚡ Performance

GravityIcons automatically pauses its physics engine when the launcher is not visible.

Sensor listeners and VSync updates stop when you leave the home screen, so the module does not continuously consume CPU or battery while using other apps.

---

## 🛠️ Requirements

* **Android:** 8.0 (API 26) → Android 16 (API 36)
* **Root:** Magisk / KernelSU / APatch with Zygisk
* **Xposed:** LSPosed, LSPosed-JingMatrix, or Zygisk Next

---

## 📦 Installation

### 1. Install the APK

Install:

```text
app-release-unsigned.apk
```

or build the project yourself.

### 2. Enable GravityIcons

Open:

```text
LSPosed Manager → Modules → GravityIcons
```

Enable the module.

### 3. Select your Launcher

Enable your system launcher in the module's **Scope**.

### 4. Restart the Launcher

Force stop the launcher or reboot your device.

### 5. Enjoy

Return to the home screen and shake your phone to activate gravity.

---

## 🔬 How It Works

### LSPosed Hooks

GravityIcons hooks launcher lifecycle methods:

```text
onCreate
onResume
onPause
onStop
```

It also hooks:

```text
android.view.View.draw
```

to detect launcher objects such as:

```text
BubbleTextView
FolderIcon
AppWidgetHostView
QsbContainer
```

### Native View Transformations

Physics is updated on the display's VSync using `Choreographer`.

The module applies:

```java
setTranslationX()
setTranslationY()
setRotation()
```

directly to the original Android `View` objects.

---

## ⚠️ Troubleshooting

### A. Icons don't move

Check the logs:

```bash
adb logcat -s GravityIcons XposedBridge
```

You should see messages such as:

```text
hooked android.view.View.draw
engine attached for <launcher package>
icon registered: com.android.launcher3.BubbleTextView
```

Check that:

1. GravityIcons is enabled in LSPosed.
2. Your launcher is selected in the module Scope.
3. Your launcher package is supported.
4. The launcher uses recognized icon view classes.

You can check the default launcher with:

```bash
adb shell cmd shortcut get-default-launcher
```

---

### B. Icons move, but shaking doesn't work

Try shaking the phone more sharply.

If necessary, reduce:

```text
SHAKE_THRESHOLD
```

If the device doesn't provide a suitable linear acceleration sensor, GravityIcons can fall back to accelerometer data.

---

### C. Icons barely react

Increase:

```text
IMPULSE_GAIN_PX
```

This controls how much movement is transferred to the physics engine after a shake.

---

### D. Launcher crashes

Disable the module and check:

```bash
adb logcat -b crash
```

Then check:

```bash
adb logcat -s GravityIcons XposedBridge
```

Launcher internals can differ between Android versions and manufacturers, so some launchers may require additional hooks.

---

## 📜 License

**GPL-3.0 License**

See [`LICENSE`](LICENSE) for details.

---

<div align="center">

### 🌌 GravityIcons

**Your icons. Your launcher. Gravity.**

</div>

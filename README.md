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

## ? License

Distributed under the **GPL-3.0 License**.

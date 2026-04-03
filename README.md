# Setec Labs Driver Manager

An LSPosed/Xposed module for per-app driver management on Android. Redirect native library loading to custom drivers without modifying system files.

## How It Works

Driver Manager hooks into `System.loadLibrary()`, `System.load()`, `Runtime.loadLibrary0()`, and `BaseDexClassLoader.findLibrary()` via Xposed. When a scoped app tries to load a native driver, the hook redirects it to your custom driver file. The stock system drivers are never touched on disk.

## Features

- **Per-App Driver Scoping** — Select a driver, then pick which apps use the custom version (LSPosed-style checkbox UI)
- **System-Wide Mode** — Apply a custom driver to all user apps with one toggle
- **Driver Registry** — Auto-scans `/vendor` for GPU, WiFi, Bluetooth, audio, camera, USB, and SDR drivers with SHA256 hashing
- **Multiple Variants** — Keep multiple versions of a driver and switch between them
- **Kernel Module Manager** — Load/unload `.ko` kernel modules, set autoload, check compatibility
- **Driver Protection** — SHA256 baseline integrity checking to detect unauthorized changes to system drivers
- **File Redirect Hooks** — Also intercepts `FileInputStream` for firmware/config file redirection per-app

## Requirements

- Android 10+ (API 29+)
- [Vector](https://github.com/JingMatrix/Vector) (LSPosed fork) or compatible Xposed framework
- Root (KernelSU or Magisk) for .ko management and driver scanning

## Installation

1. Build the APK or grab a release
2. Install the APK
3. Enable the module in Vector
4. Set the module scope to the apps you want to manage
5. Open Driver Manager and tap the scan button to discover system drivers
6. Enable a driver, add custom variants, and scope apps to use them

## Building

```
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

## Project Structure

```
app/src/main/
├── assets/xposed_init              # Xposed entry point
├── java/com/seteclabs/drivermanager/
│   ├── xposed/DriverHook.java      # Xposed hooks (the core)
│   ├── manager/
│   │   ├── ScopeManager.java       # Per-app scope config
│   │   ├── DriverRegistry.java     # Driver discovery + database
│   │   ├── KoManager.java          # .ko kernel module management
│   │   ├── ProtectionManager.java  # Integrity checking
│   │   └── RootShell.java          # Root command execution
│   ├── model/                       # Data classes
│   └── ui/                          # Activity + Fragments
└── res/                             # Layouts, colors, themes
```

## Config Location

```
/data/local/tmp/driver-manager/
├── scopes/          # Per-package JSON configs (read by Xposed hook)
├── drivers/         # Custom driver files
├── modules/         # .ko kernel modules
├── backup/          # Driver backups for integrity restore
└── autoload.conf    # Modules to load at boot
```

## Supported Driver Categories

| Category  | What it scans |
|-----------|---------------|
| GPU       | EGL libs, Vulkan ICDs in `/vendor/lib64/egl/`, `/vendor/lib64/hw/` |
| WiFi      | BCM/WCN/WLAN firmware in `/vendor/firmware/`, `/vendor/etc/wifi/` |
| Bluetooth | HCD/BT firmware in `/vendor/firmware/`, `/vendor/etc/bluetooth/` |
| Audio     | Audio HAL in `/vendor/lib64/hw/` |
| Camera    | Camera HAL in `/vendor/lib64/hw/` |
| USB       | libusb in `/vendor/lib64/`, `/system/lib64/` |
| SDR       | RTL-SDR, HackRF, Airspy libs (Termux) |

## License

Free for personal use. Redistribution, modification, or use beyond personal purposes requires approval. See [LICENSE](LICENSE) for full terms.

package com.seteclabs.drivermanager.xposed;

import android.app.Application;
import android.content.Context;
import android.os.Environment;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Xposed hook entry point for Driver Manager.
 *
 * Intercepts native library loading (System.loadLibrary / System.load / dlopen)
 * and redirects to custom driver files for scoped apps.
 *
 * Stock system drivers are NEVER modified on disk.
 * Custom drivers are loaded in-process only for apps the user has selected.
 */
public class DriverHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static final String TAG = "DriverManager";
    private static final String PACKAGE = "com.seteclabs.drivermanager";
    private static final String CONFIG_DIR = "/data/local/tmp/driver-manager";
    private static final String DRIVERS_DIR = "/data/local/tmp/driver-manager/drivers";

    // Map of library name -> custom path for the current package
    private Map<String, String> redirectMap = new HashMap<>();

    // Whether this package is scoped at all
    private boolean isScoped = false;

    @Override
    public void initZygote(StartupParam startupParam) {
        // Ensure config directory exists (created by the app with root)
        XposedBridge.log(TAG + ": Zygote initialized");
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // Skip our own package
        if (PACKAGE.equals(lpparam.packageName)) return;

        // Load redirect configuration for this package
        loadConfig(lpparam.packageName);
        if (!isScoped || redirectMap.isEmpty()) return;

        XposedBridge.log(TAG + ": Hooking " + lpparam.packageName
                + " with " + redirectMap.size() + " driver redirects");

        // Hook 1: System.loadLibrary(String libName)
        // This is the most common way apps load native libraries
        XposedHelpers.findAndHookMethod(
                "java.lang.System",
                lpparam.classLoader,
                "loadLibrary",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        String libName = (String) param.args[0];
                        String customPath = redirectMap.get(libName);

                        if (customPath != null) {
                            File customFile = new File(customPath);
                            if (customFile.exists() && customFile.canRead()) {
                                XposedBridge.log(TAG + ": [" + lpparam.packageName
                                        + "] Redirecting lib '" + libName + "' -> " + customPath);
                                try {
                                    System.load(customPath);
                                    param.setResult(null); // Skip original loadLibrary
                                } catch (UnsatisfiedLinkError e) {
                                    XposedBridge.log(TAG + ": Failed to load custom driver: " + e.getMessage());
                                    // Fall through to original
                                }
                            } else {
                                XposedBridge.log(TAG + ": Custom driver missing: " + customPath);
                            }
                        }
                    }
                }
        );

        // Hook 2: System.load(String absolutePath)
        // For apps that load libraries by absolute path
        XposedHelpers.findAndHookMethod(
                "java.lang.System",
                lpparam.classLoader,
                "load",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        String origPath = (String) param.args[0];
                        if (origPath == null) return;

                        // Check if the filename matches any redirect
                        String filename = new File(origPath).getName();
                        // Strip lib prefix and .so suffix to get the lib name
                        String libName = filename;
                        if (libName.startsWith("lib")) libName = libName.substring(3);
                        if (libName.endsWith(".so")) libName = libName.substring(0, libName.length() - 3);

                        String customPath = redirectMap.get(libName);
                        // Also check by full filename
                        if (customPath == null) customPath = redirectMap.get(filename);
                        // Also check by original absolute path
                        if (customPath == null) customPath = redirectMap.get(origPath);

                        if (customPath != null && new File(customPath).exists()) {
                            XposedBridge.log(TAG + ": [" + lpparam.packageName
                                    + "] Redirecting path '" + origPath + "' -> " + customPath);
                            param.args[0] = customPath;
                        }
                    }
                }
        );

        // Hook 3: Runtime.loadLibrary0(ClassLoader, Class, String)
        // Internal method that both loadLibrary and load funnel through on Android 10+
        try {
            Method loadLib0 = XposedHelpers.findMethodExact(
                    Runtime.class, "loadLibrary0",
                    ClassLoader.class, Class.class, String.class);

            XposedBridge.hookMethod(loadLib0, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String libName = (String) param.args[2];
                    if (libName == null) return;

                    // loadLibrary0 receives the library name (not "lib" prefix or ".so" suffix)
                    String customPath = redirectMap.get(libName);

                    // Also check if it's an absolute path
                    if (customPath == null && libName.startsWith("/")) {
                        String filename = new File(libName).getName();
                        String baseName = filename;
                        if (baseName.startsWith("lib")) baseName = baseName.substring(3);
                        if (baseName.endsWith(".so")) baseName = baseName.substring(0, baseName.length() - 3);
                        customPath = redirectMap.get(baseName);
                        if (customPath == null) customPath = redirectMap.get(filename);
                    }

                    if (customPath != null && new File(customPath).exists()) {
                        XposedBridge.log(TAG + ": [" + lpparam.packageName
                                + "] loadLibrary0 redirect: '" + libName + "' -> " + customPath);
                        // Replace with absolute path to custom driver
                        param.args[2] = customPath;
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Could not hook Runtime.loadLibrary0: " + t.getMessage());
        }

        // Hook 4: BaseDexClassLoader.findLibrary(String)
        // Returns the full path where a library would be loaded from
        // We redirect this to our custom path so the entire loading chain uses it
        try {
            XposedHelpers.findAndHookMethod(
                    "dalvik.system.BaseDexClassLoader",
                    lpparam.classLoader,
                    "findLibrary",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String libName = (String) param.args[0];
                            String customPath = redirectMap.get(libName);

                            if (customPath != null && new File(customPath).exists()) {
                                XposedBridge.log(TAG + ": [" + lpparam.packageName
                                        + "] findLibrary redirect: '" + libName + "' -> " + customPath);
                                param.setResult(customPath);
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Could not hook findLibrary: " + t.getMessage());
        }

        // Hook 5: Intercept file open for firmware/config file redirection
        // This catches apps that read driver configs or firmware blobs directly
        hookFileRedirects(lpparam);
    }

    /**
     * Hook file open operations to redirect firmware/config file reads.
     * This handles cases where apps read driver-adjacent files (configs, firmware blobs)
     * that aren't loaded via loadLibrary.
     */
    private void hookFileRedirects(XC_LoadPackage.LoadPackageParam lpparam) {
        // Check if we have file redirects configured
        Map<String, String> fileRedirects = loadFileRedirects(lpparam.packageName);
        if (fileRedirects == null || fileRedirects.isEmpty()) return;

        // Hook FileInputStream constructor to redirect file paths
        XposedHelpers.findAndHookConstructor(
                "java.io.FileInputStream",
                lpparam.classLoader,
                File.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        File origFile = (File) param.args[0];
                        if (origFile == null) return;

                        String origPath = origFile.getAbsolutePath();
                        String customPath = fileRedirects.get(origPath);

                        if (customPath != null && new File(customPath).exists()) {
                            XposedBridge.log(TAG + ": [" + lpparam.packageName
                                    + "] File redirect: " + origPath + " -> " + customPath);
                            param.args[0] = new File(customPath);
                        }
                    }
                }
        );

        // Also hook the String constructor variant
        XposedHelpers.findAndHookConstructor(
                "java.io.FileInputStream",
                lpparam.classLoader,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        String origPath = (String) param.args[0];
                        if (origPath == null) return;

                        String customPath = fileRedirects.get(origPath);
                        if (customPath != null && new File(customPath).exists()) {
                            XposedBridge.log(TAG + ": [" + lpparam.packageName
                                    + "] File redirect: " + origPath + " -> " + customPath);
                            param.args[0] = customPath;
                        }
                    }
                }
        );
    }

    /**
     * Load the redirect configuration for a specific package.
     * Config is stored as JSON by the Driver Manager app.
     *
     * Format: /data/local/tmp/driver-manager/scopes/{packageName}.json
     * {
     *   "libraries": {
     *     "rtlsdr": "/data/local/tmp/driver-manager/drivers/librtlsdr_custom.so",
     *     "usb-1.0": "/data/local/tmp/driver-manager/drivers/libusb_custom.so"
     *   },
     *   "files": {
     *     "/vendor/firmware/fw.bin": "/data/local/tmp/driver-manager/drivers/fw_custom.bin"
     *   }
     * }
     */
    private void loadConfig(String packageName) {
        redirectMap.clear();
        isScoped = false;

        File configFile = new File(CONFIG_DIR + "/scopes/" + packageName + ".json");
        if (!configFile.exists()) return;

        try (FileReader reader = new FileReader(configFile)) {
            Gson gson = new Gson();
            ScopeConfig config = gson.fromJson(reader, ScopeConfig.class);

            if (config != null && config.libraries != null) {
                redirectMap.putAll(config.libraries);
                isScoped = true;
            }
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Error loading config for " + packageName + ": " + e.getMessage());
        }
    }

    /**
     * Load file redirect map for a package (firmware, config files).
     */
    private Map<String, String> loadFileRedirects(String packageName) {
        File configFile = new File(CONFIG_DIR + "/scopes/" + packageName + ".json");
        if (!configFile.exists()) return null;

        try (FileReader reader = new FileReader(configFile)) {
            Gson gson = new Gson();
            ScopeConfig config = gson.fromJson(reader, ScopeConfig.class);
            return config != null ? config.files : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Scope configuration structure.
     */
    static class ScopeConfig {
        /** Map of library name -> custom .so path */
        Map<String, String> libraries;
        /** Map of original file path -> custom file path */
        Map<String, String> files;
    }
}

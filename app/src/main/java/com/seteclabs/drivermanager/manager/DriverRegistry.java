package com.seteclabs.drivermanager.manager;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.seteclabs.drivermanager.model.Driver;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the driver registry - discovers system drivers and tracks custom variants.
 * Drivers are stored as JSON in the app's internal storage and synced to the
 * shared config directory for the Xposed hook to read.
 */
public class DriverRegistry {

    private static final String REGISTRY_FILE = "drivers.json";
    private static final String CONFIG_DIR = "/data/local/tmp/driver-manager";

    private final Context context;
    private final Gson gson;
    private List<Driver> drivers = new ArrayList<>();

    public DriverRegistry(Context context) {
        this.context = context;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        load();
    }

    /**
     * Load drivers from storage.
     */
    public void load() {
        File file = new File(context.getFilesDir(), REGISTRY_FILE);
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<List<Driver>>(){}.getType();
            List<Driver> loaded = gson.fromJson(reader, type);
            if (loaded != null) drivers = loaded;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Save drivers to storage.
     */
    public void save() {
        File file = new File(context.getFilesDir(), REGISTRY_FILE);
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(drivers, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Scan the device for system drivers.
     * Uses root to read /vendor, /system paths and compute hashes.
     */
    public void scanSystem() {
        drivers.clear();

        // GPU drivers
        scanDirectory("/vendor/lib64/egl", "gpu", "lib", ".so");
        scanDirectory("/vendor/lib64/hw", "gpu", "vulkan.", ".so");

        // WiFi firmware
        scanFirmware("/vendor/firmware", "wifi", "fw_bcm", ".bin");
        scanFirmware("/vendor/firmware", "wifi", "fw_wcn", ".bin");
        scanFirmware("/vendor/etc/wifi", "wifi", "", ".bin");

        // Bluetooth firmware
        scanFirmware("/vendor/firmware", "bluetooth", "BCM", ".hcd");
        scanFirmware("/vendor/firmware", "bluetooth", "BTFW", ".hcd");
        scanFirmware("/vendor/etc/bluetooth", "bluetooth", "", ".hcd");

        // Audio HAL
        scanDirectory("/vendor/lib64/hw", "audio", "audio", ".so");

        // Camera HAL
        scanDirectory("/vendor/lib64/hw", "camera", "camera", ".so");

        // USB libraries (for SDR, etc.)
        scanDirectory("/vendor/lib64", "usb", "libusb", ".so");
        scanDirectory("/system/lib64", "usb", "libusb", ".so");

        // SDR-specific (if installed via Termux or other means)
        scanDirectory("/data/data/com.termux/files/usr/lib", "sdr", "librtlsdr", ".so");
        scanDirectory("/data/data/com.termux/files/usr/lib", "sdr", "libhackrf", ".so");
        scanDirectory("/data/data/com.termux/files/usr/lib", "sdr", "libairspy", ".so");

        save();
    }

    private void scanDirectory(String dirPath, String category, String prefix, String suffix) {
        String result = RootShell.exec("ls -1 " + dirPath + "/ 2>/dev/null");
        if (result.startsWith("ERROR") || result.isEmpty()) return;

        for (String filename : result.split("\n")) {
            filename = filename.trim();
            if (filename.isEmpty()) continue;
            if (!prefix.isEmpty() && !filename.startsWith(prefix)) continue;
            if (!suffix.isEmpty() && !filename.endsWith(suffix)) continue;

            String fullPath = dirPath + "/" + filename;
            String hash = RootShell.exec("sha256sum " + fullPath + " 2>/dev/null | cut -d' ' -f1").trim();

            // Derive library name (strip lib prefix and .so suffix)
            String libName = filename;
            if (libName.startsWith("lib")) libName = libName.substring(3);
            if (libName.endsWith(".so")) libName = libName.substring(0, libName.length() - 3);

            String id = category + "-" + libName;
            Driver driver = new Driver(id, filename, category, libName);
            driver.setStockPath(fullPath);
            driver.setStockHash(hash);
            driver.setDescription(category.toUpperCase() + " driver: " + filename);

            // Add stock as default variant
            Driver.Variant stock = new Driver.Variant("stock", "Stock", fullPath);
            stock.setHash(hash);
            driver.getVariants().add(stock);
            driver.setActiveVariant("stock");

            drivers.add(driver);
        }
    }

    private void scanFirmware(String dirPath, String category, String prefix, String suffix) {
        // Same as scanDirectory but for firmware files
        scanDirectory(dirPath, category, prefix, suffix);
    }

    /**
     * Add a custom driver variant.
     * Copies the file to the drivers directory and registers it.
     */
    public boolean addVariant(String driverId, String variantName, String sourcePath) {
        Driver driver = getDriver(driverId);
        if (driver == null) return false;

        String filename = new File(sourcePath).getName();
        String destPath = CONFIG_DIR + "/drivers/" + driverId + "_" + variantName + "_" + filename;

        // Copy file to drivers dir with root
        int code = RootShell.execCode("cp '" + sourcePath + "' '" + destPath + "' && chmod 644 '" + destPath + "'");
        if (code != 0) return false;

        String hash = RootShell.exec("sha256sum '" + destPath + "' | cut -d' ' -f1").trim();

        Driver.Variant variant = new Driver.Variant(variantName, variantName, destPath);
        variant.setHash(hash);
        driver.getVariants().add(variant);

        save();
        return true;
    }

    /**
     * Remove a driver variant.
     */
    public void removeVariant(String driverId, String variantId) {
        Driver driver = getDriver(driverId);
        if (driver == null) return;

        driver.getVariants().removeIf(v -> v.getId().equals(variantId) && !"stock".equals(v.getId()));

        if (variantId.equals(driver.getActiveVariant())) {
            driver.setActiveVariant("stock");
        }

        save();
    }

    /**
     * Set the active variant for a driver.
     */
    public void setActiveVariant(String driverId, String variantId) {
        Driver driver = getDriver(driverId);
        if (driver == null) return;
        driver.setActiveVariant(variantId);
        save();
    }

    public Driver getDriver(String id) {
        for (Driver d : drivers) {
            if (d.getId().equals(id)) return d;
        }
        return null;
    }

    public List<Driver> getDrivers() { return drivers; }

    public List<Driver> getDriversByCategory(String category) {
        List<Driver> result = new ArrayList<>();
        for (Driver d : drivers) {
            if (d.getCategory().equals(category)) result.add(d);
        }
        return result;
    }

    public List<Driver> getEnabledDrivers() {
        List<Driver> result = new ArrayList<>();
        for (Driver d : drivers) {
            if (d.isEnabled()) result.add(d);
        }
        return result;
    }

    /**
     * Verify a driver's current hash matches the registered stock hash.
     */
    public String verifyDriver(String driverId) {
        Driver driver = getDriver(driverId);
        if (driver == null) return "not_found";

        String currentHash = RootShell.exec("sha256sum '" + driver.getStockPath() + "' | cut -d' ' -f1").trim();
        if (currentHash.equals(driver.getStockHash())) return "ok";
        return "changed";
    }
}

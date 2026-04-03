package com.seteclabs.drivermanager.manager;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.seteclabs.drivermanager.model.Driver;
import com.seteclabs.drivermanager.model.Scope;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages per-app driver scoping.
 *
 * The scope config is written to /data/local/tmp/driver-manager/scopes/{package}.json
 * which the Xposed hook reads at app startup to know what to redirect.
 *
 * System drivers are NEVER modified. Only the in-process library loading path
 * is redirected for scoped apps.
 */
public class ScopeManager {

    private static final String CONFIG_DIR = "/data/local/tmp/driver-manager";
    private static final String SCOPES_DIR = CONFIG_DIR + "/scopes";
    private static final String SCOPE_MAP_FILE = "scope_map.json";

    private final Context context;
    private final Gson gson;
    private final DriverRegistry registry;

    /**
     * Master scope map: driverId -> set of package names.
     * This is the UI-friendly view. We convert this to per-package scope files
     * that the Xposed hook reads.
     */
    private Map<String, Set<String>> scopeMap = new HashMap<>();

    public ScopeManager(Context context, DriverRegistry registry) {
        this.context = context;
        this.registry = registry;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        load();
    }

    /**
     * Load the master scope map from app storage.
     */
    public void load() {
        File file = new File(context.getFilesDir(), SCOPE_MAP_FILE);
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Set<String>>>(){}.getType();
            Map<String, Set<String>> loaded = gson.fromJson(reader, type);
            if (loaded != null) scopeMap = loaded;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Save the master scope map.
     */
    private void saveMaster() {
        File file = new File(context.getFilesDir(), SCOPE_MAP_FILE);
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(scopeMap, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Add an app to a driver's scope.
     */
    public void addAppToScope(String driverId, String packageName) {
        scopeMap.computeIfAbsent(driverId, k -> new HashSet<>()).add(packageName);
        saveMaster();
        writePerPackageConfig(packageName);
    }

    /**
     * Remove an app from a driver's scope.
     */
    public void removeAppFromScope(String driverId, String packageName) {
        Set<String> apps = scopeMap.get(driverId);
        if (apps != null) {
            apps.remove(packageName);
            if (apps.isEmpty()) scopeMap.remove(driverId);
        }
        saveMaster();
        writePerPackageConfig(packageName);
    }

    /**
     * Check if an app is scoped for a driver.
     */
    public boolean isAppScoped(String driverId, String packageName) {
        Set<String> apps = scopeMap.get(driverId);
        return apps != null && apps.contains(packageName);
    }

    /**
     * Get all packages scoped for a driver.
     */
    public Set<String> getScopedApps(String driverId) {
        return scopeMap.getOrDefault(driverId, new HashSet<>());
    }

    /**
     * Get all drivers scoped for a package.
     */
    public List<String> getScopedDrivers(String packageName) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : scopeMap.entrySet()) {
            if (entry.getValue().contains(packageName)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Set scope for all apps at once (system-wide for a driver).
     * Adds every installed non-system app to the scope.
     */
    public void setScopeSystemWide(String driverId) {
        Set<String> allApps = new HashSet<>();
        PackageManager pm = context.getPackageManager();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                allApps.add(ai.packageName);
            }
        }
        scopeMap.put(driverId, allApps);
        saveMaster();
        syncAllConfigs();
    }

    /**
     * Clear all scopes for a driver.
     */
    public void clearScope(String driverId) {
        Set<String> oldApps = scopeMap.remove(driverId);
        saveMaster();
        if (oldApps != null) {
            for (String pkg : oldApps) {
                writePerPackageConfig(pkg);
            }
        }
    }

    /**
     * Write the per-package scope config file that the Xposed hook reads.
     *
     * For each scoped package, we create:
     * /data/local/tmp/driver-manager/scopes/{package}.json
     * containing all library and file redirects for that package.
     */
    private void writePerPackageConfig(String packageName) {
        Scope scope = new Scope();

        // Build the redirect map from all drivers scoped to this package
        for (Map.Entry<String, Set<String>> entry : scopeMap.entrySet()) {
            if (!entry.getValue().contains(packageName)) continue;

            String driverId = entry.getKey();
            Driver driver = registry.getDriver(driverId);
            if (driver == null || !driver.isEnabled()) continue;

            String customPath = driver.getActiveVariantPath();
            if (customPath == null || "stock".equals(driver.getActiveVariant())) continue;

            // Library redirect (for System.loadLibrary hooks)
            if (driver.getLibraryName() != null && !driver.getLibraryName().isEmpty()) {
                scope.addLibrary(driver.getLibraryName(), customPath);
            }

            // File redirect (for direct file access hooks)
            if (driver.getStockPath() != null) {
                scope.addFile(driver.getStockPath(), customPath);
            }
        }

        String scopeFilePath = SCOPES_DIR + "/" + packageName + ".json";

        if (scope.isEmpty()) {
            // No redirects for this package, remove the config file
            RootShell.exec("rm -f '" + scopeFilePath + "'");
        } else {
            // Write the config file
            String json = gson.toJson(scope);
            RootShell.exec("echo '" + json.replace("'", "'\\''") + "' > '" + scopeFilePath + "'");
            RootShell.exec("chmod 644 '" + scopeFilePath + "'");
        }
    }

    /**
     * Rebuild all per-package config files.
     * Call after changing driver variants or enabling/disabling drivers.
     */
    public void syncAllConfigs() {
        // Collect all unique packages
        Set<String> allPackages = new HashSet<>();
        for (Set<String> apps : scopeMap.values()) {
            allPackages.addAll(apps);
        }

        // Clear old configs
        RootShell.exec("rm -f " + SCOPES_DIR + "/*.json");

        // Write new configs
        for (String pkg : allPackages) {
            writePerPackageConfig(pkg);
        }
    }

    /**
     * Get installed apps for the scope selection UI.
     */
    public List<AppInfo> getInstalledApps() {
        List<AppInfo> result = new ArrayList<>();
        PackageManager pm = context.getPackageManager();

        for (ApplicationInfo ai : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            AppInfo info = new AppInfo();
            info.packageName = ai.packageName;
            info.label = pm.getApplicationLabel(ai).toString();
            info.isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            info.icon = ai;
            result.add(info);
        }

        // Sort: user apps first, then alphabetically
        result.sort((a, b) -> {
            if (a.isSystem != b.isSystem) return a.isSystem ? 1 : -1;
            return a.label.compareToIgnoreCase(b.label);
        });

        return result;
    }

    /**
     * App info for the scope selection UI.
     */
    public static class AppInfo {
        public String packageName;
        public String label;
        public boolean isSystem;
        public ApplicationInfo icon;
    }
}

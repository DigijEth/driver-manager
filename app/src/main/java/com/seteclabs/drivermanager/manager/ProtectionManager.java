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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monitors system driver integrity by comparing current hashes against a baseline.
 * Does NOT modify system files. Uses root to read and hash files.
 */
public class ProtectionManager {

    private static final String BASELINE_FILE = "baseline.json";
    private static final String BACKUP_DIR = "/data/local/tmp/driver-manager/backup";

    private final Context context;
    private final DriverRegistry registry;
    private final Gson gson;

    /** Map of file path -> expected SHA256 hash */
    private Map<String, String> baseline = new HashMap<>();

    public ProtectionManager(Context context, DriverRegistry registry) {
        this.context = context;
        this.registry = registry;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadBaseline();
    }

    /**
     * Create a baseline snapshot of all protected drivers.
     */
    public int createBaseline() {
        baseline.clear();
        int count = 0;

        for (Driver driver : registry.getDrivers()) {
            if (!driver.isEnabled()) continue; // Only baseline enabled/protected drivers
            String path = driver.getStockPath();
            if (path == null || path.isEmpty()) continue;

            String hash = RootShell.exec("sha256sum '" + path + "' 2>/dev/null | cut -d' ' -f1").trim();
            if (!hash.isEmpty() && !hash.startsWith("ERROR")) {
                baseline.put(path, hash);
                count++;

                // Backup the file
                String backupPath = BACKUP_DIR + path;
                String backupDir = backupPath.substring(0, backupPath.lastIndexOf('/'));
                RootShell.exec("mkdir -p '" + backupDir + "' && cp -p '" + path + "' '" + backupPath + "'");
            }
        }

        saveBaseline();
        return count;
    }

    /**
     * Check all baselined files for changes.
     * @return list of check results
     */
    public List<CheckResult> checkIntegrity() {
        List<CheckResult> results = new ArrayList<>();

        for (Map.Entry<String, String> entry : baseline.entrySet()) {
            String path = entry.getKey();
            String expectedHash = entry.getValue();

            CheckResult result = new CheckResult();
            result.path = path;
            result.expectedHash = expectedHash;

            String exists = RootShell.exec("[ -f '" + path + "' ] && echo yes || echo no").trim();
            if (!"yes".equals(exists)) {
                result.status = "missing";
                result.actualHash = "";
            } else {
                result.actualHash = RootShell.exec("sha256sum '" + path + "' | cut -d' ' -f1").trim();
                result.status = result.actualHash.equals(expectedHash) ? "ok" : "changed";
            }

            results.add(result);
        }

        return results;
    }

    /**
     * Get summary stats.
     */
    public Stats getStats() {
        Stats stats = new Stats();
        stats.totalProtected = baseline.size();

        List<CheckResult> results = checkIntegrity();
        for (CheckResult r : results) {
            switch (r.status) {
                case "ok": stats.ok++; break;
                case "changed": stats.changed++; break;
                case "missing": stats.missing++; break;
            }
        }
        return stats;
    }

    private void loadBaseline() {
        File file = new File(context.getFilesDir(), BASELINE_FILE);
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> loaded = gson.fromJson(reader, type);
            if (loaded != null) baseline = loaded;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveBaseline() {
        File file = new File(context.getFilesDir(), BASELINE_FILE);
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(baseline, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class CheckResult {
        public String path;
        public String expectedHash;
        public String actualHash;
        public String status; // ok, changed, missing
    }

    public static class Stats {
        public int totalProtected;
        public int ok;
        public int changed;
        public int missing;
    }
}

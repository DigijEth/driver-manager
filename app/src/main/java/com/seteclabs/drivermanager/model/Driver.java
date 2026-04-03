package com.seteclabs.drivermanager.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a driver that can be managed (redirected per-app).
 */
public class Driver {
    private String id;
    private String name;
    private String category; // gpu, wifi, bluetooth, audio, sdr, usb, misc
    private String description;
    private String stockPath;     // Original system path (e.g., /vendor/lib64/egl/libEGL_powervr.so)
    private String stockHash;     // SHA256 of stock file
    private String libraryName;   // Name used in System.loadLibrary() (e.g., "rtlsdr")
    private boolean enabled;
    private List<Variant> variants = new ArrayList<>();
    private String activeVariant;  // ID of currently selected variant

    public Driver() {}

    public Driver(String id, String name, String category, String libraryName) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.libraryName = libraryName;
        this.enabled = false;
    }

    // --- Getters/Setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStockPath() { return stockPath; }
    public void setStockPath(String stockPath) { this.stockPath = stockPath; }
    public String getStockHash() { return stockHash; }
    public void setStockHash(String stockHash) { this.stockHash = stockHash; }
    public String getLibraryName() { return libraryName; }
    public void setLibraryName(String libraryName) { this.libraryName = libraryName; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<Variant> getVariants() { return variants; }
    public void setVariants(List<Variant> variants) { this.variants = variants; }
    public String getActiveVariant() { return activeVariant; }
    public void setActiveVariant(String activeVariant) { this.activeVariant = activeVariant; }

    /**
     * Get the path of the currently active variant.
     */
    public String getActiveVariantPath() {
        if (activeVariant == null) return null;
        for (Variant v : variants) {
            if (v.getId().equals(activeVariant)) return v.getPath();
        }
        return null;
    }

    /**
     * A driver variant (e.g., stock, custom v1, custom v2).
     */
    public static class Variant {
        private String id;
        private String name;
        private String path;
        private String hash;
        private long size;

        public Variant() {}

        public Variant(String id, String name, String path) {
            this.id = id;
            this.name = name;
            this.path = path;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getHash() { return hash; }
        public void setHash(String hash) { this.hash = hash; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
    }
}

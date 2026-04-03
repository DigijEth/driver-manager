package com.seteclabs.drivermanager.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-app scope configuration.
 * Defines which driver libraries and files get redirected for a specific package.
 */
public class Scope {
    /** Map of library name -> custom .so path */
    private Map<String, String> libraries = new HashMap<>();

    /** Map of original file path -> custom file path */
    private Map<String, String> files = new HashMap<>();

    public Scope() {}

    public Map<String, String> getLibraries() { return libraries; }
    public void setLibraries(Map<String, String> libraries) { this.libraries = libraries; }
    public Map<String, String> getFiles() { return files; }
    public void setFiles(Map<String, String> files) { this.files = files; }

    public void addLibrary(String name, String customPath) {
        libraries.put(name, customPath);
    }

    public void removeLibrary(String name) {
        libraries.remove(name);
    }

    public void addFile(String originalPath, String customPath) {
        files.put(originalPath, customPath);
    }

    public void removeFile(String originalPath) {
        files.remove(originalPath);
    }

    public boolean isEmpty() {
        return libraries.isEmpty() && files.isEmpty();
    }
}

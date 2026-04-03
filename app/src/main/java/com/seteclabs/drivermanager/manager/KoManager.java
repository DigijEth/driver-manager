package com.seteclabs.drivermanager.manager;

import com.seteclabs.drivermanager.model.KernelModule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages .ko kernel modules via root shell.
 * Modules are stored in /data/local/tmp/driver-manager/modules/
 */
public class KoManager {

    private static final String MODULES_DIR = "/data/local/tmp/driver-manager/modules";
    private static final String AUTOLOAD_FILE = "/data/local/tmp/driver-manager/autoload.conf";

    /**
     * List all .ko files in the modules directory with their status.
     */
    public List<KernelModule> listModules() {
        List<KernelModule> modules = new ArrayList<>();

        String files = RootShell.exec("ls -1 " + MODULES_DIR + "/*.ko 2>/dev/null");
        if (files.isEmpty() || files.startsWith("ERROR")) return modules;

        // Get currently loaded modules
        String lsmod = RootShell.exec("lsmod 2>/dev/null");
        Set<String> loadedModules = new HashSet<>();
        for (String line : lsmod.split("\n")) {
            String name = line.split("\\s+")[0];
            if (!"Module".equals(name)) loadedModules.add(name);
        }

        // Get autoload list
        String autoloadContent = RootShell.exec("cat " + AUTOLOAD_FILE + " 2>/dev/null");
        Set<String> autoloadSet = new HashSet<>();
        for (String line : autoloadContent.split("\n")) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) autoloadSet.add(line);
        }

        // Get running kernel version
        String kernelVer = RootShell.exec("uname -r").trim();

        for (String filepath : files.split("\n")) {
            filepath = filepath.trim();
            if (filepath.isEmpty()) continue;

            KernelModule mod = new KernelModule();
            String filename = filepath.substring(filepath.lastIndexOf('/') + 1);
            mod.setFilename(filename);

            // Get modinfo
            String info = RootShell.exec("modinfo " + filepath + " 2>/dev/null");
            for (String line : info.split("\n")) {
                if (line.startsWith("name:")) mod.setName(line.substring(5).trim());
                else if (line.startsWith("description:")) mod.setDescription(line.substring(12).trim());
                else if (line.startsWith("version:")) mod.setVersion(line.substring(8).trim());
                else if (line.startsWith("author:")) mod.setAuthor(line.substring(7).trim());
                else if (line.startsWith("depends:")) mod.setDepends(line.substring(8).trim());
                else if (line.startsWith("vermagic:")) mod.setVermagic(line.substring(9).trim());
            }

            if (mod.getName() == null) mod.setName(filename.replace(".ko", ""));

            // File size
            String size = RootShell.exec("stat -c%s " + filepath + " 2>/dev/null").trim();
            try { mod.setSize(Long.parseLong(size)); } catch (NumberFormatException e) { mod.setSize(0); }

            // Check if loaded
            mod.setLoaded(loadedModules.contains(mod.getName()));

            // Check autoload
            mod.setAutoload(autoloadSet.contains(filename));

            // Check kernel version compatibility
            if (mod.getVermagic() != null && !mod.getVermagic().isEmpty()) {
                String modKver = mod.getVermagic().split("\\s+")[0];
                mod.setCompatible(modKver.equals(kernelVer));
            }

            modules.add(mod);
        }

        return modules;
    }

    /**
     * Load a kernel module.
     * @return result message
     */
    public String loadModule(String filename, String params) {
        String filepath = MODULES_DIR + "/" + filename;

        // Check if file exists
        String exists = RootShell.exec("[ -f '" + filepath + "' ] && echo yes || echo no").trim();
        if (!"yes".equals(exists)) return "Module file not found: " + filename;

        // Load dependencies first
        String depends = RootShell.exec("modinfo -F depends '" + filepath + "' 2>/dev/null").trim();
        if (!depends.isEmpty()) {
            for (String dep : depends.split(",")) {
                dep = dep.trim();
                if (dep.isEmpty()) continue;
                String loaded = RootShell.exec("lsmod | grep -q '^" + dep + " ' && echo yes || echo no").trim();
                if (!"yes".equals(loaded)) {
                    String depFile = MODULES_DIR + "/" + dep + ".ko";
                    String depExists = RootShell.exec("[ -f '" + depFile + "' ] && echo yes || echo no").trim();
                    if ("yes".equals(depExists)) {
                        RootShell.exec("insmod '" + depFile + "'");
                    } else {
                        RootShell.exec("modprobe '" + dep + "'");
                    }
                }
            }
        }

        String paramStr = params != null ? params : "";
        String result = RootShell.exec("insmod '" + filepath + "' " + paramStr + " 2>&1");
        if (result.isEmpty()) return "OK";
        return result;
    }

    /**
     * Unload a kernel module.
     */
    public String unloadModule(String name) {
        String result = RootShell.exec("rmmod '" + name + "' 2>&1");
        if (result.isEmpty()) return "OK";
        return result;
    }

    /**
     * Set autoload on/off for a module.
     */
    public void setAutoload(String filename, boolean enabled) {
        if (enabled) {
            // Add to autoload if not already there
            RootShell.exec("grep -qxF '" + filename + "' " + AUTOLOAD_FILE
                    + " || echo '" + filename + "' >> " + AUTOLOAD_FILE);
        } else {
            RootShell.exec("sed -i '/^" + filename + "$/d' " + AUTOLOAD_FILE);
        }
    }

    /**
     * Load all modules in the autoload list.
     */
    public void autoloadAll() {
        String content = RootShell.exec("cat " + AUTOLOAD_FILE + " 2>/dev/null");
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            loadModule(line, null);
        }
    }

    /**
     * Unload all managed modules.
     */
    public void unloadAll() {
        List<KernelModule> modules = listModules();
        for (KernelModule mod : modules) {
            if (mod.isLoaded()) {
                unloadModule(mod.getName());
            }
        }
    }

    /**
     * Get detailed modinfo output for a module.
     */
    public String getModuleInfo(String filename) {
        return RootShell.exec("modinfo " + MODULES_DIR + "/" + filename + " 2>/dev/null");
    }

    /**
     * Get recent dmesg output related to module loading.
     */
    public String getModuleDmesg() {
        return RootShell.exec("dmesg | tail -30 2>/dev/null");
    }
}

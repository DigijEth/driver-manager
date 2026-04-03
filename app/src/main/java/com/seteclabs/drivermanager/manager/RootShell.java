package com.seteclabs.drivermanager.manager;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

/**
 * Execute commands via su (KernelSU/Magisk root).
 * Used for operations that need root: .ko loading, file hashing, driver scanning.
 */
public class RootShell {

    /**
     * Execute a command as root and return stdout.
     */
    public static String exec(String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            process.waitFor();
            reader.close();
            os.close();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
        return output.toString().trim();
    }

    /**
     * Execute a command as root, return exit code.
     */
    public static int execCode(String command) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            return process.waitFor();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Check if root is available.
     */
    public static boolean hasRoot() {
        return execCode("id") == 0;
    }

    /**
     * Ensure the config directory structure exists.
     */
    public static void ensureConfigDirs() {
        exec("mkdir -p /data/local/tmp/driver-manager/scopes");
        exec("mkdir -p /data/local/tmp/driver-manager/drivers");
        exec("mkdir -p /data/local/tmp/driver-manager/modules");
        exec("mkdir -p /data/local/tmp/driver-manager/backup");
        exec("chmod -R 755 /data/local/tmp/driver-manager");
    }
}

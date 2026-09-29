package org.example.ui.utilities;

import java.io.File;
import java.nio.file.Paths;

public class DirectoryCleaner {
    public static void cleanFolders() {
        // Download and screenshot folders, including the per-country subfolders
        clearDirectory(Paths.get(System.getProperty("user.dir"), CountryPaths.DEFAULT_DOWNLOADS).toFile());
        clearDirectory(Paths.get(System.getProperty("user.dir"), CountryPaths.SCREENSHOTS).toFile());
    }

    private static void clearDirectory(File dir) {
        if (!dir.exists()) dir.mkdirs(); // create if not exists
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            try {
                if (f.isDirectory()) clearDirectory(f); // keep the folder, remove its files
                else f.delete();
            } catch (Exception ignored) {
            }
        }
    }
}

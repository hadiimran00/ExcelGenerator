package org.example.ui.utilities;

import java.io.File;
import java.nio.file.Paths;

public class DirectoryCleaner {
    public static void cleanFolders() {
        // Download folder
        String downloadDir = Paths.get(System.getProperty("user.dir"), "DownloadedExcels").toAbsolutePath().toString();
        DirectoryCleaner.clearDirectory(downloadDir);

        // Screenshot folder
        String screenshotDir = Paths.get(System.getProperty("user.dir"), "Screenshots").toAbsolutePath().toString();
        DirectoryCleaner.clearDirectory(screenshotDir);
    }

    private static void clearDirectory(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists()) dir.mkdirs(); // create if not exists
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            try {
                f.delete();
            } catch (Exception ignored) {
            }
        }
    }
}

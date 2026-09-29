package org.example.ui.utilities;

import org.example.ui.Main;

import java.io.File;
import java.nio.file.Paths;

/**
 * Resolves per-country output folders (downloads, screenshots) for the user currently being executed,
 * e.g. DownloadedExcels/Pakistan and Screenshots/Pakistan.
 */
public class CountryPaths {
    public static final String DEFAULT_DOWNLOADS = "DownloadedExcels";
    public static final String SCREENSHOTS = "Screenshots";

    /** Country of the running user, made safe for use as a folder name; "Unknown" when none is set. */
    public static String country() {
        String country = Main.currentUser != null ? Main.currentUser.getCountry() : null;
        if (country == null || country.isBlank()) return "Unknown";
        return country.trim().replaceAll("[^A-Za-z0-9_-]+", "_");
    }

    /** {@code <cwd>/<baseDir>/<Country>}, created if missing. */
    public static File dir(String baseDir) {
        File dir = Paths.get(System.getProperty("user.dir"), baseDir, country()).toFile();
        dir.mkdirs();
        return dir;
    }

    public static File downloadDir() {
        return dir(DEFAULT_DOWNLOADS);
    }

    public static File screenshotDir() {
        return dir(SCREENSHOTS);
    }
}

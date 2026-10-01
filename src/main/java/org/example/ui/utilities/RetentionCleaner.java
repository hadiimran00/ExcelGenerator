package org.example.ui.utilities;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Properties;

// Deletes per-run folders under DownloadedExcels/<Country>/<run> and Screenshots/<Country>/<run>
// older than the configured retention window, so history from timestamped run folders
// (see Main.runTimestamp) doesn't grow unbounded.
public class RetentionCleaner {
    private static final Logger logger = LoggerUtil.getLogger(RetentionCleaner.class);

    public static void cleanOldRuns(Properties properties) {
        int retentionDays;
        try {
            retentionDays = Integer.parseInt(properties.getProperty("retentionDays", "3").trim());
        } catch (NumberFormatException e) {
            logger.warn("⚠️ Invalid 'retentionDays' value in application.properties; defaulting to 3");
            retentionDays = 3;
        }

        long cutoffMillis = System.currentTimeMillis() - (retentionDays * 24L * 60 * 60 * 1000);

        cleanBaseFolder(new File(System.getProperty("user.dir"), "DownloadedExcels"), cutoffMillis);
        cleanBaseFolder(new File(System.getProperty("user.dir"), "Screenshots"), cutoffMillis);
    }

    // baseDir/<country>/<run> - deletes <run> folders whose last-modified time predates the cutoff
    private static void cleanBaseFolder(File baseDir, long cutoffMillis) {
        File[] countryDirs = baseDir.listFiles(File::isDirectory);
        if (countryDirs == null) return;

        int deleted = 0;
        for (File countryDir : countryDirs) {
            File[] runDirs = countryDir.listFiles(File::isDirectory);
            if (runDirs == null) continue;

            for (File runDir : runDirs) {
                if (runDir.lastModified() < cutoffMillis) {
                    try {
                        FileUtils.deleteDirectory(runDir);
                        deleted++;
                    } catch (Exception e) {
                        logger.warn("⚠️ Failed to delete old run folder {}: {}", runDir, e.getMessage());
                    }
                }
            }
        }
        if (deleted > 0) {
            logger.info("🧹 Retention cleanup: removed {} old run folder(s) under {}", deleted, baseDir.getName());
        }
    }
}

package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

/**
 * Pauses a flow for a step the tester does by hand (e.g. DB scripts the automation must not run itself).
 * The scripts are written to the log; the flow continues when continue.txt appears in the working directory
 * or Enter is pressed in the console, and fails after the timeout.
 */
public class ManualStep {

    private static final Logger logger = LoggerUtil.getLogger(ManualStep.class);

    public static final Path CONTINUE_FILE = Paths.get("continue.txt");
    private static final long POLL_MS = 5000;

    public static void pause(String title, List<String> scripts, Duration timeout) throws InterruptedException, IOException {
        // A file left from an earlier pause must not let this one through
        Files.deleteIfExists(CONTINUE_FILE);
        StringBuilder banner = new StringBuilder("\n===== MANUAL STEP: ").append(title).append(" =====\n");
        scripts.forEach(script -> banner.append(script).append('\n'));
        banner.append("To continue: create ").append(CONTINUE_FILE.toAbsolutePath()).append(" (or press Enter in the console)\n")
                .append("Waiting up to ").append(timeout.toMinutes()).append(" minutes\n")
                .append("=".repeat(title.length() + 32));
        logger.info(banner.toString());

        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(CONTINUE_FILE)) {
                Files.deleteIfExists(CONTINUE_FILE);
                logger.info("Manual step '{}' confirmed - continuing", title);
                return;
            }
            if (enterPressed()) {
                logger.info("Manual step '{}' confirmed in the console - continuing", title);
                return;
            }
            Thread.sleep(POLL_MS);
        }
        throw new IllegalStateException("Manual step '" + title + "' not confirmed within " + timeout.toMinutes() + " minutes");
    }

    // A background run has no console input; only a run from the IDE/terminal can press Enter
    private static boolean enterPressed() {
        try {
            if (System.in.available() > 0) {
                while (System.in.available() > 0) {
                    System.in.read();
                }
                return true;
            }
        } catch (IOException ignored) {
            // no console attached
        }
        return false;
    }
}

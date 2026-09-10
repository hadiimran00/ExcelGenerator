package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;
import java.util.List;

public class ToastHandles {
    private static final Logger logger = LoggerUtil.getLogger(ToastHandles.class);

    /**
     * Waits for any notification message starting with ID 'notify_text_'.
     */
    public static String waitForNotification(WebDriver driver, Duration timeout) {
        return waitForNotification(driver, timeout, null);
    }

    /**
     * Waits for a notification, while optionally monitoring and dismissing
     * an intermediate confirmation dialog (e.g. clicking 'Yes') if it appears.
     */
    public static String waitForNotification(WebDriver driver, Duration timeout, By confirmationButtonLocator) {
        final String[] capturedMessage = { "" };
        try {
            new WebDriverWait(driver, timeout).until(d -> {
                try {
                    // Handle intermediate dialog dynamically if configured
                    if (confirmationButtonLocator != null) {
                        List<WebElement> confirmButtons = d.findElements(confirmationButtonLocator);
                        if (!confirmButtons.isEmpty() && confirmButtons.get(0).isDisplayed()) {
                            Event.robustClick(d, confirmationButtonLocator);
                            logger.info("👍 Confirmation dialog handled via locator: {}", confirmationButtonLocator);
                        }
                    }

                    // Scan for active toast/notification content
                    List<WebElement> notes = d.findElements(By.xpath("//*[starts-with(@id, 'notify_text_')]"));
                    for (WebElement note : notes) {
                        String text = note.getText().trim();
                        if (!text.isEmpty()) {
                            capturedMessage[0] = text;
                            return true;
                        }
                    }
                } catch (StaleElementReferenceException e) {
                    // Retries automatically on next polling interval
                    return false;
                }
                return false;
            });
        } catch (TimeoutException e) {
            logger.warn("⚠️ [TIMEOUT] Toast notification did not appear within {} seconds.", timeout.toSeconds());
            ScreenshotService.takeScreenshot(driver, "Toast notification did not appear");

        }
        return capturedMessage[0];
    }
}
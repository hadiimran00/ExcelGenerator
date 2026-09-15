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
    /**
     * Validates that the active notification contains the default keyword "success".
     */
    public static String validateSuccessMessage(WebDriver driver, Duration timeout, ValidationResult result) {
        return validateSuccessMessage(driver, timeout, null, "success", result);
    }

    /**
     * Validates that the active notification contains the specified expected substring (e.g., "success").
     * Updates the ValidationResult via result.pass() or result.fail() and captures screenshots on failure.
     *
     * @param driver                  WebDriver instance
     * @param timeout                 Maximum wait duration
     * @param confirmationButtonLocator Optional locator for pop-up buttons (e.g., 'Yes')
     * @param expectedMessageSubstring Snippet expected in the toast
     * @param result                  ValidationResult object to log pass/fail status
     * @return The actual captured message string
     */
    public static String validateSuccessMessage(
            WebDriver driver,
            Duration timeout,
            By confirmationButtonLocator,
            String expectedMessageSubstring,
            ValidationResult result
    ) {
        String actualToast = waitForNotification(driver, timeout, confirmationButtonLocator);

        if (actualToast == null || actualToast.trim().isEmpty()) {
            String errorMsg = String.format("Validation Failed: No notification toast appeared within %d seconds. Expected containing: '%s'",
                    timeout.toSeconds(), expectedMessageSubstring);

            logger.error("❌ " + errorMsg);
            ScreenshotService.takeScreenshot(driver, "Missing_Toast_Notification");

            if (result != null) {
                result.fail(errorMsg);
            }
            throw new AssertionError(errorMsg);
        }

        if (actualToast.toLowerCase().contains(expectedMessageSubstring.toLowerCase())) {
            String passMsg = String.format("Toast Validation Passed! Expected text: '%s' | Actual Toast: '%s'",
                    expectedMessageSubstring, actualToast);

            logger.info("✅ " + passMsg);

            if (result != null) {
                result.pass(passMsg);
            }
        } else {
            String errorMsg = String.format("Validation Failed! Toast message did not contain '%s'. Actual Toast: '%s'",
                    expectedMessageSubstring, actualToast);

            logger.error("❌ " + errorMsg);
            ScreenshotService.takeScreenshot(driver, "Toast_Validation_Failure");

            if (result != null) {
                result.fail(errorMsg);
            }
            throw new AssertionError(errorMsg);
        }

        return actualToast;
    }
}
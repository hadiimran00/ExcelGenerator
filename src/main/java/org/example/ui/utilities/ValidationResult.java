package org.example.ui.utilities;

import org.openqa.selenium.WebDriver;

import java.util.ArrayList;
import java.util.List;

public class ValidationResult {

    public final String screenName;
    public boolean passed = true;
    public final List<String> passes   = new ArrayList<>();
    public final List<String> failures = new ArrayList<>();

    // The report shows one image per row: the first failure's screenshot (later failures usually follow from it),
    // or the latest success screenshot while nothing has failed.
    public String screenshotBase64 = "";
    private boolean failureScreenshot = false;

    public ValidationResult(String screenName) {
        this.screenName = screenName;
    }

    public void pass(String detail) {
        passes.add(detail);
    }

    public void pass(String detail, WebDriver driver) {
        pass(detail);
        ScreenshotService.takeScreenshot(driver, screenName + "_Success");
        // A success after a failure must not replace the failure's screenshot in the report
        if (!failureScreenshot) {
            screenshotBase64 = ScreenshotService.getBase64Screenshot(driver);
        }
    }

    public void fail(String detail) {
        // Most failures are recorded without a driver: take the screenshot from the current browser
        fail(detail, ScreenshotService.getCurrentDriver());
    }

    public void fail(String detail, WebDriver driver) {
        passed = false;
        failures.add(detail);
        if (driver != null) {
            captureErrorScreenshot(driver);
        }
    }

    // Every failure is saved as a file; the report row keeps the first one, the screen where things went wrong
    private void captureErrorScreenshot(WebDriver driver) {
        ScreenshotService.takeScreenshot(driver, screenName + "_Error");
        if (!failureScreenshot) {
            screenshotBase64 = ScreenshotService.getBase64Screenshot(driver);
            failureScreenshot = true;
        }
    }

    public void logSummary(org.apache.logging.log4j.Logger logger) {
        if (passed) {
            logger.info("✅ Validation PASSED [{}]", screenName);
        } else {
            logger.info("❌ Validation FAILED [{}]", screenName);
            failures.forEach(f -> logger.info("   ↳ {}", f));
        }
    }
}
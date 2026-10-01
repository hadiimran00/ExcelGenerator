package org.example.ui.utilities;

import org.openqa.selenium.WebDriver;

import java.util.ArrayList;
import java.util.List;

public class ValidationResult {

    public final String screenName;
    public boolean passed = true;
    public final List<String> passes   = new ArrayList<>();
    public final List<String> failures = new ArrayList<>();

    // Set by fail(detail, driver) - the report can only show one image per row, so this holds
    // the most recent failure's screenshot rather than one per individual failure.
    public String screenshotBase64 = "";

    public ValidationResult(String screenName) {
        this.screenName = screenName;
    }

    public void pass(String detail) {
        passes.add(detail);
    }

    public void pass(String detail, WebDriver driver) {
        pass(detail);
        ScreenshotService.takeScreenshot(driver, screenName + "_Success");
        screenshotBase64 = ScreenshotService.getBase64Screenshot(driver);
    }

    public void fail(String detail) {
        passed = false;
        failures.add(detail);
    }

    public void fail(String detail, WebDriver driver) {
        fail(detail);
        ScreenshotService.takeScreenshot(driver, screenName + "_Error");
        screenshotBase64 = ScreenshotService.getBase64Screenshot(driver);
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
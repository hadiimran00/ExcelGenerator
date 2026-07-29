package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;
import java.util.List;

public class UIValidator {

    private static final Logger logger = LoggerUtil.getLogger(UIValidator.class);
    private static final Duration WAIT = Duration.ofSeconds(10);

    public static void navigateSearchAndVerify(
            WebDriver driver,
            String searchColumn,
            String searchValue,
            ValidationResult result, String columnId) {

        try {
            Event.robustClick(driver, By.id("gridFilterCheckbox"));

            By searchBoxLocator = By.id(columnId);
            WebDriverWait wait = new WebDriverWait(driver, WAIT);
            WebElement searchBox = wait.until(
                    ExpectedConditions.elementToBeClickable(searchBoxLocator)
            );
            if (searchBox == null) {
                result.fail("No search box found on screen: " + searchColumn);
                ScreenshotService.takeScreenshot(driver, "no_searchbox_" + searchColumn);
                return;
            }

            searchBox.clear();
            searchBox.sendKeys(searchValue);
            logger.info("🔎 Typed search value [{}] into columnId [{}]", searchValue, columnId);

            waitForGridRows(driver, wait);

            boolean found = isValueInColumn(driver, searchColumn, searchValue);
            logger.info("🔎 Row found in grid for value [{}]? {}", searchValue, found);

            if (!found) {
                result.fail("Value '" + searchValue + "' NOT found in grid on screen: " + searchColumn);
                ScreenshotService.takeScreenshot(driver, "ui_val_fail_" + searchValue);
                logger.info("❌ UI Validation failed — '{}' not found", searchValue);
                return;
            }

            wait.until(ExpectedConditions.or(
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".dx-data-row td")),
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".dx-empty-message"))
            ));

            logger.info("鼠标 Clicking on row to open record...");
            Event.robustClick(driver, By.cssSelector(".dx-data-row td"));
            LoaderWait.waitForLoaderToDisappear(driver);

            // Wait for existing toasts/notifications to clear
            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(@class, 'dx-toast-message')]")));

            logger.info("鼠标 Clicking Update button...");
            Event.robustClick(driver, By.id("updateBtn"));

            // Polling using the new ToastHandler (dismisses 'yes' button if it appears)
            String finalMsg = ToastHandles.waitForNotification(driver, Duration.ofSeconds(30), By.id("yes"));

            if (finalMsg.toLowerCase().contains("successful") || finalMsg.toLowerCase().contains("success")) {
                result.pass("Record " + searchValue + " Found in Grid and Updated successfully: " + finalMsg);
                logger.info("✅ {}", finalMsg);
                ScreenshotService.takeScreenshot(driver, "PJP_HQ");
            } else if (!finalMsg.isEmpty()) {
                result.fail("Record " + searchValue + " Found but Update failed: " + finalMsg);
                ScreenshotService.takeScreenshot(driver, "update_failed");
                logger.error("❌ {}", finalMsg);
            } else {
                result.fail("No success/error notification appeared after update.");
                ScreenshotService.takeScreenshot(driver, "update_timeout");
            }

        } catch (Exception e) {
            result.fail("UI validation error: " + e.getMessage());
            ScreenshotService.takeScreenshot(driver, "ui_val_error");
            logger.info("UI validation exception", e);
        }
    }

    private static void waitForGridRows(WebDriver driver, WebDriverWait wait) {
        try {
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".dx-data-row td")),
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".dx-empty-message"))
            ));
        } catch (TimeoutException e) {
            logger.info("Timed out waiting for grid rows");
        }
    }

    private static boolean isValueInColumn(WebDriver driver, String searchColumn, String searchValue) {
        String xpath = "//tr[contains(@class,'dx-data-row')]"
                + "//td[contains(@id,'_" + searchColumn + "') "
                + "and @title='" + searchValue + "']";

        return !driver.findElements(By.xpath(xpath)).isEmpty();
    }
}
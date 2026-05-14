package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;
import java.time.Duration;
import java.util.List;

//import static org.example.ui.utilities.PostUploadValidator.str;

public class UIValidator {

    private static final Logger logger = LoggerUtil.getLogger(UIValidator.class);
    private static final Duration WAIT = Duration.ofSeconds(10);

    /**
     * Navigate to a grid screen via its menu item id, then search and verify a row exists.
     *
     * @param searchColumn      the element id of the menu link
     * @param menuSearchValue the value to type into the MENU search bar (new parameter)
     * @param searchValue     the value to type into the GRID search box (the record to find)
     * @param result          collects pass/fail
     */
    public static void navigateSearchAndVerify(
            WebDriver driver,
            String searchColumn,
            String searchValue,
            ValidationResult result,String columnId) {



        try {

            driver.findElement(By.id("gridFilterCheckbox")).click();

            By searchBoxLocator = By.id(columnId);
            WebDriverWait wait =
                    new WebDriverWait(driver, WAIT);
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

            // Wait for grid to settle
            waitForGridRows(driver, wait);

            // Look for the value anywhere in the visible grid
            boolean found = isValueInColumn(
                    driver,
                    searchColumn,
                    searchValue
            );

            if (found) {
                result.pass("Found '" + searchValue + "' in grid on screen: " + searchColumn);
                logger.info("✅ UI Validation passed — '{}' found in grid", searchValue);
            } else {
                result.fail("Value '" + searchValue + "' NOT found in grid on screen: " + searchColumn);
                ScreenshotService.takeScreenshot(driver, "ui_val_fail_" + searchValue);
                logger.info("❌ UI Validation failed — '{}' not found", searchValue);
            }

        } catch (Exception e) {
            result.fail("UI validation error: " + e.getMessage());
            ScreenshotService.takeScreenshot(driver, "ui_val_error");
            logger.error("UI validation exception", e);
        }
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    /**
     * Ensures menu is open and types the specific search keyword into the menu search box
     */
    private static void ensureMenuAndSearch(WebDriver driver, WebDriverWait wait, String menuSearchValue) {
        try {
            WebElement menuSearchBox;
            try {
                menuSearchBox = driver.findElement(By.cssSelector("input[placeholder='Search Here']"));
            } catch (NoSuchElementException e) {
                // If not found, click hamburger to open
                wait.until(ExpectedConditions.elementToBeClickable(By.id("menurollin"))).click();
                menuSearchBox = wait.until(ExpectedConditions.visibilityOfElementLocated(
                        By.cssSelector("input[placeholder='Search Here']")));
            }

            // Type the parameter from Excel into the menu search
            menuSearchBox.clear();
            menuSearchBox.sendKeys(menuSearchValue);
            // Small sleep to allow menu filtered results to appear
            Thread.sleep(500);

        } catch (Exception e) {
            logger.info("Could not perform menu search: {}", e.getMessage());
        }
    }

    private static WebElement findSearchBox(WebDriver driver, WebDriverWait wait) {
        String[] candidates = {
                "#rowfilter_TXT__EPP2DESC",
                "input[placeholder='Search Here']",
                "input[placeholder='Search...']",
                ".dx-texteditor-input[type='text']"
        };
        for (String css : candidates) {
            try {
                return wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(css)));
            } catch (TimeoutException ignored) {}
        }
        return null;
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

    private static boolean isValueInGrid(WebDriver driver, String value) {
        String xpath = String.format(
                "//tr[contains(@class,'dx-data-row')]//td[normalize-space()='%s']",
                value
        );
        return !driver.findElements(By.xpath(xpath)).isEmpty();
    }
    private static boolean isValueInColumn(
            WebDriver driver,
            String columnName,
            String value) {

        String xpath =
                "//tr[contains(@class,'dx-data-row')]"
                        + "//td[contains(@id,'_" + columnName + "') "
                        + "and @title='" + value + "']";

        return !driver.findElements(By.xpath(xpath)).isEmpty();
    }
}
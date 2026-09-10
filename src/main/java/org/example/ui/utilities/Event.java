package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class Event {
    private static final Logger logger = LoggerUtil.getLogger(Event.class);
    /**
     * Waits for an element to be clickable, scrolls it into view, and uses a JS click as a fallback.
     */
    public static void robustClick(WebDriver driver, By locator) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        try {
            WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            element.click();

        } catch (Exception e) {
            logger.info("⚠️ Standard click failed for locator: {} — reason: {}. Falling back to JS click.",
                    locator, e.getMessage());
            try {
          wait.until(ExpectedConditions.presenceOfElementLocated(locator));
                WebElement element = driver.findElement(locator); // Re-find to avoid stale element
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
                logger.info("✅ JS click fallback succeeded for locator: {}", locator);
            } catch (Exception jsException) {
                ScreenshotService.takeScreenshot(driver, "-");
                logger.error("❌ Both standard and JS click failed for locator: {}", locator);
                throw jsException;
            }
        }
    }
    public static void robustSendKeys(WebDriver driver, WebDriverWait wait, By locator, String text) {
        try {
            WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
            element.clear();
            element.sendKeys(text);
        } catch (StaleElementReferenceException e) {
            LoggerUtil.getLogger(Event.class).warn("⚠️ Element became stale. Re-finding and retrying sendKeys...");
            WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
            element.clear();
            element.sendKeys(text);
        }
    }
}
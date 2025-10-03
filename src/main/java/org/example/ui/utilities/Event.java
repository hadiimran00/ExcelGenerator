package org.example.ui.utilities;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class Event {
    /**
     * Waits for an element to be clickable, scrolls it into view, and uses a JS click as a fallback.
     * This is highly robust for headless and CI/CD environments.
     */
    public static void robustClick(WebDriver driver, By locator) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        try {
            WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
            element.click();
        } catch (Exception e) {
            //System.out.println("⚠️ Standard click failed, attempting JS click for locator: " + locator);
            try {
                WebElement element = driver.findElement(locator); // Re-find to avoid stale element
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            } catch (Exception jsException) {
                ScreenshotService.takeScreenshot(driver);
                System.out.println("❌ Both standard and JS click failed for locator: " + locator);
                throw jsException; // Re-throw the exception to fail the test
            }
        }
    }
}

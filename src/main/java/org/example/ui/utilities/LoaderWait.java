//package org.example.ui.utilities;
//
//import org.openqa.selenium.By;
//import org.openqa.selenium.TimeoutException;
//import org.openqa.selenium.WebDriver;
//import org.openqa.selenium.WebElement;
//import org.openqa.selenium.support.ui.ExpectedConditions;
//import org.openqa.selenium.support.ui.WebDriverWait;
//import org.apache.logging.log4j.Logger;
//
//import java.time.Duration;
//
//public class LoaderWait {
//    private static final Logger logger = LoggerUtil.getLogger(LoaderWait.class);
//    public static void waitForLoaderToDisappear(WebDriver driver) {
//        By loader = By.cssSelector(".dx-loadindicator-wrapper");
//
//        try {
//            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(2));
//
//            // Wait until loader is actually visible on the UI (not just present)
//            WebElement visibleLoader = shortWait.until(ExpectedConditions.visibilityOfElementLocated(loader));
//
//            // Only log if loader appeared
//            if (visibleLoader != null) {
//                logger.debug("⏳ Loader appeared, waiting to disappear...");
//
//                // Wait until loader disappears
//                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
//                wait.until(ExpectedConditions.invisibilityOfElementLocated(loader));
//
//                logger.debug("✅ Loader disappeared.");
//            }
//
//        } catch (TimeoutException e) {
//            // Loader never appeared → safe to continue
//            logger.debug("✅ Loader did not appear, continuing execution.");
//        }
//    }
//
//
//}
package org.example.ui.utilities;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.apache.logging.log4j.Logger;

import java.time.Duration;

public class LoaderWait {
    private static final Logger logger = LoggerUtil.getLogger(LoaderWait.class);

    // devExpress spinners & overlay backdrops
    private static final By LOAD_INDICATOR = By.cssSelector(".dx-loadindicator-wrapper");
    private static final By OVERLAY_SHADER  = By.cssSelector(".dx-overlay-shader");

    /**
     * Waits for DevExpress loaders and overlay shaders to fully disappear.
     */
    public static void waitForLoaderToDisappear(WebDriver driver) {
        waitForLoaderToDisappear(driver, 600);
    }

    public static void waitForLoaderToDisappear(WebDriver driver, long timeoutInSeconds) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutInSeconds));

        try {
            // 1. Wait until any visible loader indicator disappears
            wait.until(ExpectedConditions.invisibilityOfElementLocated(LOAD_INDICATOR));

            // 2. Wait until DevExpress overlay backdrop disappears (prevents click interception)
            wait.until(ExpectedConditions.invisibilityOfElementLocated(OVERLAY_SHADER));

        } catch (TimeoutException e) {
            logger.warn("⚠️ Loader/Overlay did not completely disappear within {} seconds.", timeoutInSeconds);
        }
    }
}
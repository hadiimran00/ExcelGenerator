package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.LoggerFactory;

public class validateCashmemo{

    private static final Logger logger = LoggerUtil.getLogger(validateCashmemo.class);

    public static void validateCashmemoForLMT(WebDriver driver, WebDriverWait wait) throws InterruptedException {
        // Select "Completed" in dropdown
        try {
            WebElement element = wait.until(ExpectedConditions.elementToBeClickable(By.id("DDL__EORI_ERROR_STATUSPARENT")));
            Thread.sleep(1000);
            element.clear();
            element.sendKeys("Completed");
            Thread.sleep(500);

            WebElement item = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("(//div[@id='dropdown-content']//*[contains(text(), 'Completed')])[1]")
            ));
            item.click();
            Thread.sleep(500);

            // Click file name row
            Event.robustClick(driver, By.id("row_1_file_name"));

            // Wait for the message and scroll into view
            WebElement message = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//td[normalize-space(text())='Cashmemo created successfully']")
                    )
            );
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", message);
            Thread.sleep(300);

            // Get cashmemo number
            WebElement cashmemoNoEl = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("row_1_cashmemo_no_")));
            String cashmemoNo = cashmemoNoEl.getText();
            // Validate the message
            String actualMessage = message.getText().trim();
            if (actualMessage.equals("Cashmemo created successfully")) {
                logger.info("✅ Cashmemo created successfully! with Order No: {} ", cashmemoNo);
            } else {
                logger.info("❌ Order Creation Failed! Actual message: {} ", actualMessage);
                ScreenshotService.takeScreenshot(driver);
            }
        } catch (InterruptedException e) {
            logger.info("❌ Unexpected error during Cashmemo validation: {}", e.getMessage(), e);
            ScreenshotService.takeScreenshot(driver);
        }
    }
}

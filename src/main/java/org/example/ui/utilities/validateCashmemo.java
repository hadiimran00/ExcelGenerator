package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;


public class validateCashmemo {

    private static final Logger logger = LoggerUtil.getLogger(validateCashmemo.class);

    public static void validateCashmemoForLMT(WebDriver driver, WebDriverWait wait, ValidationResult result,String testId) {

        String Tmessage=null;
        try {

            // ----- FUNCTION TO SELECT STATUS -----
            Runnable selectCompleted = () -> {
                WebElement dropdown = wait.until(ExpectedConditions.elementToBeClickable(
                        By.id("DDL__EORI_ERROR_STATUSPARENT")));
                dropdown.click();
                dropdown.clear();
                dropdown.sendKeys("Completed");

                WebElement option = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("(//div[@id='dropdown-content']//*[contains(text(), 'Completed')])[1]")));
                option.click();
            };

            Runnable selectInProcess = () -> {
                WebElement dropdown = wait.until(ExpectedConditions.elementToBeClickable(
                        By.id("DDL__EORI_ERROR_STATUSPARENT")));
                dropdown.click();
                dropdown.clear();
                dropdown.sendKeys("In Progress");

                WebElement option = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("(//div[@id='dropdown-content']//*[contains(text(), 'In Progress')])[1]")));
                option.click();
            };

            // ----- INITIAL ATTEMPT -----
            selectCompleted.run();
            Thread.sleep(2000);

            try {
                Event.robustClick(driver, By.id("row_1_file_name"));
            } catch (Exception e) {
                logger.info("Uploaded cashmemos not found!. Refreshing...");

                selectInProcess.run();
                Thread.sleep(3000);

                selectCompleted.run();
                Thread.sleep(3000);

                Event.robustClick(driver, By.id("row_1_file_name"));
            }

            // ----- VALIDATION SECTION -----
            // Read the message cell's actual (rendered) text once via getText(), which captures
            // nested elements - a td[text()='...'] xpath only matches direct text-node children,
            // so it silently fails to find a genuine success message wrapped in a nested span/div.
            WebElement messageEl = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("(//td[@id='row_1_message' and normalize-space()!=''])[2]")
                    )
            );
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", messageEl);
            Thread.sleep(300);
            Tmessage = messageEl.getText().trim();

            if (Tmessage.toLowerCase().contains("success")) {
                WebElement cashmemoNoEl = wait.until(
                        ExpectedConditions.visibilityOfElementLocated(By.id("row_1_cashmemo_no_")));
                String cashmemoNo = cashmemoNoEl.getText();

                logger.info("✅ {} with Order No: {}", Tmessage, cashmemoNo);
                result.pass(Tmessage + " Order No:" + cashmemoNo, driver);
                GeneratedDataStore.store(testId, "cashmemoNo", cashmemoNo);
            } else {
                logger.info("❌ Validation Failed: {}", Tmessage);
                result.fail("LMT validation failed for testId [" + testId + "]: " + Tmessage, driver);
            }

        } catch (Exception e) {
            logger.info("❌ Unexpected error during Cashmemo validation setup: {} {}",
                    Tmessage,
                    e.getMessage(),
                    e);

            result.fail("Unexpected error during Cashmemo validation setup: " + e.getMessage(), driver);
        }
    }
}

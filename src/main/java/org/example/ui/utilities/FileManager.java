package org.example.ui.utilities;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import static org.example.ui.utilities.ExcelValidator.logExcelErrors;
import static org.example.ui.utilities.LoaderWait.waitForLoaderToDisappear;
import static org.example.ui.utilities.ScreenshotService.takeScreenshot;
import static org.example.ui.utilities.TestSummary.*;
public class FileManager {
    private static final Logger logger = LoggerUtil.getLogger(FileManager.class);
    public static void uploadFile(WebDriver driver, String screenName, String filePath) throws InterruptedException {
        waitForLoaderToDisappear(driver);
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(@class, 'dx-toast-message')]")));
            logger.info("⬆ Uploading: {}", filePath);
            WebElement fileInput = driver.findElement(By.cssSelector("input[type='file']"));
            fileInput.sendKeys(filePath);
            Thread.sleep(1000);
            WebElement toast = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//*[contains(@class, 'dx-toast-message')]")
            ));
        } catch (Exception e) {
            logger.info("❌ Upload failed on screen: {}", screenName, e);
            takeScreenshot(driver,screenName);
        }
        //waitForLoaderToDisappear(driver);
      //  waitForLoaderAndToast(driver);
        // Check for success message
        //WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(
                    d -> !d.findElements(By.id("notify_text_success")).isEmpty() && !d.findElements(By.id("notify_text_success")).get(0).getText().trim().isEmpty()
                            || !d.findElements(By.id("notify_text_error")).isEmpty() && !d.findElements(By.id("notify_text_error")).get(0).getText().trim().isEmpty()
            );
        } catch (TimeoutException ignored) {}
        List < WebElement > successMsgList = driver.findElements(By.id("notify_text_success"));
        if (!successMsgList.isEmpty() && successMsgList.get(0).getText().contains("File upload successful")) {
            WebElement successMsg = successMsgList.get(0);
            logger.info("✅ Success: {}", successMsg.getText());
            recordUploadSuccess(screenName);
            takeScreenshot(driver,screenName);
        } else {
            // Check for error message

            List < WebElement > errorMsgList = driver.findElements(By.id("notify_text_error"));
            if (!errorMsgList.isEmpty() && errorMsgList.get(0).getText().contains("Error while processing excel file, file downloaded.")) {
                WebElement errorMsg = errorMsgList.get(0);
                logger.info("❌ Upload Failed! {} ", errorMsg.getText());
                recordUploadFailure(screenName, errorMsg.getText());
                takeScreenshot(driver,screenName);
                //checking for error file downlaoded
                File downloadDir = new File(System.getProperty("user.dir"), "DownloadedExcels");
                File[] files = downloadDir.listFiles((dir,name) -> name.toLowerCase().endsWith(".xlsx"));
                if (files != null && files.length > 0) {
                    // Pick the latest downloaded file
                    File latestFile = Arrays.stream(files).max(Comparator.comparingLong(File::lastModified)).orElse(null);
                    if (latestFile != null && latestFile.length() > 0) {
                        logger.info("📂 Found error file: {} ", latestFile.getName());
                        logExcelErrors(latestFile);
                    }
                }
            } else if (!errorMsgList.isEmpty() && errorMsgList.get(0).isDisplayed()) {
                WebElement errorMsg = errorMsgList.get(0);
                logger.info("❌ Upload Failed! {} ", errorMsg.getText());
                recordUploadFailure(screenName, errorMsg.getText());
            } else {
                String message = "❌ Could not find notification message.";
                logger.info(message);
                recordUploadFailure(screenName, message);
            }
        }
    }
    public static void downloadExcel(WebDriver driver, String screenName, Map < String, String > params) throws InterruptedException, IOException {
        waitForLoaderToDisappear(driver);
        for (Map.Entry<String, String> field : params.entrySet()) {
            String paramId = field.getKey();
            String value = field.getValue();
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
            try {
                waitForLoaderToDisappear(driver);
                WebElement element = wait.until(ExpectedConditions.elementToBeClickable(By.id(paramId)));
                logger.info("-> Filling field ID: [{}] with Value: [{}]", paramId, value);
                Thread.sleep(2500);
               // waitForLoaderToDisappear(driver);
                element.clear();
                element.sendKeys(value);
                Thread.sleep(500); // Small pause for UI to react
                // If value is not a date, then try to click the dropdown
                if (!value.matches("\\d{4}-\\d{2}-\\d{2}|\\d{2}-\\d{2}-\\d{4}")) {
                    try {
                        WebElement item = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("(//div[@id='dropdown-content']//*[contains(text(), '" + value + "')])[1]")));
                        item.click();
                    } catch (TimeoutException e) {
                        takeScreenshot(driver,screenName);
                        logger.info("⚠️ No dropdown item found for: {} (This may be data issue. Please check your config file.)", value);
                    }
                }
            } catch (Exception e) {
                logger.info("❌ Could not process element: {} with value {}. Error: {}", paramId, value, e.getMessage());
            }
        }
        logger.info("⬇ Downloading Excel for: {} ", screenName);
        Thread.sleep(1000);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(@class, 'dx-toast-message')]")));

        WebElement downloadBtn = null;
        try {
            downloadBtn = driver.findElement(By.xpath("//button[contains(text(),'Download Excel')]"));
            downloadBtn.click();
        } catch (Exception e) {
            try {
                Event.robustClick(driver, By.id("downloadExcel"));
            } catch (Exception ex) {
                logger.info(ex.getMessage());
            }
        }
        // Wait a bit for messages to appear
        //waitForLoaderToDisappear(driver);
       // waitForLoaderAndToast(driver);
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(
                    d -> !d.findElements(By.id("notify_text_success")).isEmpty() && !d.findElements(By.id("notify_text_success")).get(0).getText().trim().isEmpty()
                            || !d.findElements(By.id("notify_text_error")).isEmpty() && !d.findElements(By.id("notify_text_error")).get(0).getText().trim().isEmpty()
            );
        } catch (TimeoutException ignored) {}
        List < WebElement > successMsgList = driver.findElements(By.id("notify_text_success"));
        if (!successMsgList.isEmpty() && successMsgList.get(0).getText().contains("File downloaded successfully")) {
            WebElement successMsg = successMsgList.get(0);
            String text = successMsg.getText();
            logger.info("✅ Success: {}", text);
            File downloadDir = new File(System.getProperty("user.dir"), "DownloadedExcels");
            File[] files = downloadDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx") || name.toLowerCase().endsWith(".csv"));
            // Pick the latest downloaded file
            File latestFile = Arrays.stream(files).max(Comparator.comparingLong(File::lastModified)).orElse(null);
            if (latestFile != null && latestFile.length() > 0) {
                logger.info("📂 Found file: {}", latestFile.getName());
                recordDownloadSuccess(screenName);
                takeScreenshot(driver,screenName);
            } else {
                logger.info("❌ Downloaded File not found!");
            }
        } else {
            WebElement errorMsg = null;
            takeScreenshot(driver,screenName);
            List<WebElement> errorMsgList = driver.findElements(By.id("notify_text_error"));
            if (!errorMsgList.isEmpty() && errorMsgList.get(0).isDisplayed()) {
                errorMsg = errorMsgList.get(0);
                logger.info("❌ Download Failed! {}", errorMsg.getText());
                recordDownloadFailure(screenName, errorMsg.getText());
            } else {
                String message = "❌ Could not find notification message.";
                logger.info(message);
                recordDownloadFailure(screenName, message);
            }
        }
    }

}
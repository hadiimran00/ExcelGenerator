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
        final String[] capturedMessage = { "" };

        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(@class, 'dx-toast-message')]")));
            logger.info("⬆ Uploading: {}", filePath);
            WebElement fileInput = driver.findElement(By.cssSelector("input[type='file']"));
            fileInput.sendKeys(filePath);

            Thread.sleep(1500);
        } catch (Exception e) {
            logger.error("❌ [FATAL] Upload trigger failed: {}", screenName, e);
            takeScreenshot(driver, screenName + "_Error");
            return;
        }

        try {
            new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> {
                try {
                    List<WebElement> allNotes = d.findElements(By.xpath("//*[starts-with(@id, 'notify_text_')]"));

                    for (WebElement note : allNotes) {
                        String text = note.getText().trim();
                        if (!text.isEmpty()) {
                            capturedMessage[0] = text;
                            return true;
                        }
                    }
                } catch (StaleElementReferenceException e) {
                    return false;
                }
                return false;
            });
        } catch (TimeoutException e) {
            logger.error("⚠️ [TIMEOUT] No notification appeared.", e);
        }

        String finalMsg = capturedMessage[0];

        if (finalMsg.toLowerCase().contains("successful") || finalMsg.toLowerCase().contains("success")) {
            recordUploadSuccess(screenName, ScreenshotService.getBase64Screenshot(driver));
            logger.info("✅ Success: {}", finalMsg);
        } else if (!finalMsg.isEmpty()) {
            String base64 = ScreenshotService.getBase64Screenshot(driver);
            recordUploadFailure(screenName, finalMsg, base64);

            if (finalMsg.toLowerCase().contains("file downloaded") || finalMsg.toLowerCase().contains("error")) {
                handleErrorFile(screenName);
            }
        } else {
            recordUploadFailure(screenName, "No notification detected", ScreenshotService.getBase64Screenshot(driver));
        }
    }

    public static void downloadExcel(WebDriver driver, String screenName, Map<String, String> params) throws InterruptedException, IOException {
        waitForLoaderToDisappear(driver);
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, String> field : params.entrySet()) {
                String paramId = field.getKey();
                String value = field.getValue();
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

                try {
                    waitForLoaderToDisappear(driver);
                    WebElement element = wait.until(ExpectedConditions.elementToBeClickable(By.id(paramId)));
                    logger.info("-> Filling field ID: [{}] with Value: [{}]", paramId, value);
                    Thread.sleep(1000);
                    element.clear();
                    element.sendKeys(value);
                    Thread.sleep(500);

                    if (!value.matches("\\d{4}-\\d{2}-\\d{2}|\\d{2}-\\d{2}-\\d{4}")) {
                        try {
                            WebElement item = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("(//div[@id='dropdown-content']//*[contains(text(), '" + value + "')])[1]")));
                            item.click();
                        } catch (TimeoutException e) {
                            takeScreenshot(driver, screenName);
                            logger.info("⚠️ No dropdown item found for: {} (This may be data issue.)", value);
                        }
                    }
                } catch (Exception e) {
                    logger.info("❌ Process element error: {}", e.getMessage());
                }
            }
        }
        logger.info("⬇ Downloading Excel for: {} ", screenName);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(@class, 'dx-toast-message')]")));

        try {
            WebElement downloadBtn = driver.findElement(By.xpath("//button[contains(text(),'Download Excel')]"));
            downloadBtn.click();
        } catch (Exception e) {
            try {
                Event.robustClick(driver, By.id("downloadExcel"));
            } catch (Exception ex) {
                logger.info(ex.getMessage());
            }
        }

        try {
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(d ->
                    (!d.findElements(By.id("notify_text_success")).isEmpty() && !d.findElements(By.id("notify_text_success")).get(0).getText().trim().isEmpty())
                            || (!d.findElements(By.id("notify_text_error")).isEmpty() && !d.findElements(By.id("notify_text_error")).get(0).getText().trim().isEmpty())
            );
        } catch (TimeoutException ignored) {}

        List<WebElement> successMsgList = driver.findElements(By.id("notify_text_success"));

        if (!successMsgList.isEmpty() && successMsgList.get(0).getText().contains("File downloaded successfully")) {
            WebElement successMsg = successMsgList.get(0);
            String text = successMsg.getText();
            logger.info("✅ Success: {}", text);

            File downloadDir = new File(System.getProperty("user.dir"), "DownloadedExcels");
            File[] files = downloadDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx") || name.toLowerCase().endsWith(".csv"));
            File latestFile = Arrays.stream(files).max(Comparator.comparingLong(File::lastModified)).orElse(null);

            if (latestFile != null && latestFile.length() > 0) {
                logger.info("📂 Found file: {}", latestFile.getName());
                String base64Image = ScreenshotService.getBase64Screenshot(driver);
                recordDownloadSuccess(screenName, base64Image);
            } else {
                logger.info("❌ Downloaded File not found!");
                String base64Image = ScreenshotService.getBase64Screenshot(driver);
                recordDownloadFailure(screenName, "File not found in directory", base64Image);
            }
        } else {
            String base64Image = ScreenshotService.getBase64Screenshot(driver);
            List<WebElement> errorMsgList = driver.findElements(By.id("notify_text_error"));

            if (!errorMsgList.isEmpty() && errorMsgList.get(0).isDisplayed()) {
                String errorText = errorMsgList.get(0).getText();
                logger.info("❌ Download Failed! {}", errorText);
                recordDownloadFailure(screenName, errorText, base64Image);
            } else {
                logger.info("❌ Download Failed! Could not find notification message.");
                recordDownloadFailure(screenName, "Could not find notification message.", base64Image);
            }
        }
    }

    private static void handleErrorFile(String screenName) {
        File downloadDir = new File(System.getProperty("user.dir"), "DownloadedExcels");
        File[] files = downloadDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx"));

        if (files != null && files.length > 0) {
            File latestFile = Arrays.stream(files).max(Comparator.comparingLong(File::lastModified)).orElse(null);
            if (latestFile != null && latestFile.length() > 0) {
                logger.info("📂 Found error file: {}", latestFile.getName());
                logExcelErrors(latestFile);
            }
        }
    }
}
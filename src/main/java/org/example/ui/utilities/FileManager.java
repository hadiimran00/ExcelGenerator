package org.example.ui.utilities;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;



import static org.example.ui.utilities.ExcelValidator.logExcelErrors;
import static org.example.ui.utilities.ScreenshotService.takeScreenshot;
import static org.example.ui.utilities.TestSummary.*;

public class FileManager {
    public static void uploadFile(WebDriver driver, String filePath) throws InterruptedException {
        try {
            System.out.println("⬆ Uploading: " + filePath);
            Thread.sleep(1000);
            WebElement fileInput = driver.findElement(By.cssSelector("input[type='file']"));
            fileInput.sendKeys(filePath);
            Thread.sleep(500);
        } catch (Exception e) {
            takeScreenshot(driver);
        }

// Check for success message
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        List<WebElement> successMsgList = driver.findElements(By.id("notify_text_success"));
        if (!successMsgList.isEmpty() && successMsgList.get(0).isDisplayed() && successMsgList.get(0).getText().contains("File upload successful")) {
            WebElement successMsg = successMsgList.get(0);
            System.out.println("✅ Success: " + successMsg.getText());
            recordUploadSuccess();

        } else {
            recordUploadFailure();

            // Check for error message
            takeScreenshot(driver);
            List<WebElement> errorMsgList = driver.findElements(By.id("notify_text_error"));
            if (!errorMsgList.isEmpty() && errorMsgList.get(0).isDisplayed() && errorMsgList.get(0).getText().contains("Error while processing excel file, file downloaded.")) {
                WebElement errorMsg = errorMsgList.get(0);
                System.out.println("❌ Upload Failed! " + errorMsg.getText());

                //checking for error file downlaoded
                File downloadDir = new File(System.getProperty("user.dir"), "DownloadedExcels");
                File[] files = downloadDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx"));
                if (files != null && files.length > 0) {
                    // Pick the latest downloaded file
                    File latestFile = Arrays.stream(files)
                            .max(Comparator.comparingLong(File::lastModified))
                            .orElse(null);

                    if (latestFile != null && latestFile.length() > 0) {
                        System.out.println("📂 Found error file: " + latestFile.getName());
                        logExcelErrors(latestFile);
                    }
                }
            } else if (!errorMsgList.isEmpty() && errorMsgList.get(0).isDisplayed()) {
                WebElement errorMsg = errorMsgList.get(0);
                System.out.println("❌ Upload Failed! " + errorMsg.getText());

            } else {
                System.out.println("❌ Could not find notification message.");
            }

        }}

    public static void downloadExcel(WebDriver driver, String screenName, Map<String, String> params) throws InterruptedException, IOException {
        for (Map.Entry<String, String> field : params.entrySet()) {
            String paramId = field.getKey();
            String value = field.getValue();
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

            try {
                WebElement element = wait.until(ExpectedConditions.elementToBeClickable(By.id(paramId)));

                System.out.println("-> Filling field ID: [" + paramId + "] with Value: [" + value + "]");
                Thread.sleep(1000);
                element.clear();
                element.sendKeys(value);
                Thread.sleep(500); // Small pause for UI to react

                // If value is not a date, then try to click the dropdown
                if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    try {
                        WebElement item = wait.until(ExpectedConditions.elementToBeClickable(
                                By.xpath("(//div[@id='dropdown-content']//*[contains(text(), '" + value + "')])[1]")
                        ));
                        item.click();

                    } catch (TimeoutException e) {
                        takeScreenshot(driver);
                        System.out.println("⚠️ No dropdown item found for: " + value + " (This may be data issue. Please check your config file.)");
                    }
                }

            } catch (Exception e) {
                System.out.println("❌ Could not process element: " + paramId + " with value " + value + ". Error: " + e.getMessage());
            }
        }

        System.out.println("⬇ Downloading Excel for: " + screenName);
        Thread.sleep(1000);

        WebElement downloadBtn = null;
        try {
            downloadBtn = driver.findElement(By.xpath("//button[contains(text(),'Download Excel')]"));
            downloadBtn.click();

        } catch (Exception e) {
            try {
                Event.robustClick(driver, By.id("downloadExcel"));
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }
        // Wait a bit for messages to appear
        Thread.sleep(1000);

//        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
//        try {
//            wait.until(ExpectedConditions.or(
//                    ExpectedConditions.visibilityOfElementLocated(By.id("notify_text_success")),
//                    ExpectedConditions.visibilityOfElementLocated(By.id("notify_text_error"))
//            ));
//        } catch (TimeoutException ignored)
//        {
//                if (downloadBtn != null) { //for click again
//                    downloadBtn.click();
//                }
//        }

        List<WebElement> successMsgList = driver.findElements(By.id("notify_text_success"));
        if (!successMsgList.isEmpty() && successMsgList.get(0).isDisplayed() && successMsgList.get(0).getText().contains("File downloaded successfully")) {
            WebElement successMsg = successMsgList.get(0);
            String text = successMsg.getText();
            System.out.println("✅ Success: " + text);
            File downloadDir = new File(System.getProperty("user.dir"), "DownloadedExcels");
            File[] files = downloadDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".xlsx") || name.toLowerCase().endsWith(".csv"));

            // Pick the latest downloaded file
            File latestFile = Arrays.stream(files)
                    .max(Comparator.comparingLong(File::lastModified))
                    .orElse(null);

            if (latestFile != null && latestFile.length() > 0) {
                System.out.println("📂 Found file: " + latestFile.getName());
                recordDownloadSuccess();
            } else {
                System.out.println("❌ Downloaded File not found!");
            }

        } else {
            recordDownloadFailure();
            takeScreenshot(driver);
            List<WebElement> errorMsgList = driver.findElements(By.id("notify_text_error"));
            if (!errorMsgList.isEmpty() && errorMsgList.get(0).isDisplayed()) {
                WebElement errorMsg = errorMsgList.get(0);
                System.out.println("❌ Download Failed! " + errorMsg.getText());
            } else {
                System.out.println("❌ Could not find notification message.");
            }
        }


    }
}

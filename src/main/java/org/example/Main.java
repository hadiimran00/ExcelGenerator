package org.example;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;
import java.util.Map;

import java.time.Duration;
import java.util.stream.Collectors;
import java.util.LinkedHashMap; // Import added for LinkedHashMap

public class Main {

    public static void main(String[] args) throws Exception {
        // --- Setup Selenium ---
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        WebDriver driver = new ChromeDriver(options);
        String configPath="D:\\Automation\\ExcelGenerator\\Resources\\config.xlsx";

        driver.manage().window().maximize();
        driver.get("https://dcodecnr2dev1.unilever.com/ngui");
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));

        // --- Login ---
        driver.findElement(By.id("a3")).sendKeys("kpo_ph");
        driver.findElement(By.id("a4")).sendKeys("Cent@123");
        driver.findElement(By.cssSelector("button[type='submit']")).click();

        List<Map<String, Object>> screens = ExcelLoader.loadScreens(configPath);

        for (Map<String, Object> screen : screens) {
            String screenName   = (String) screen.get("screenName");
            String screenId     = (String) screen.get("screenId");
            String mode         = (String) screen.get("mode");
            String templatePath = (String) screen.get("templatePath");

            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) screen.get("params");
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> rules = (Map<String, Map<String, Object>>) screen.get("rules");

            // Convert to Map<String, String> while preserving order
            Map<String, String> stringParams = params.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> String.valueOf(e.getValue()),
                            (oldValue, newValue) -> oldValue,
                            LinkedHashMap::new
                    ));

            System.out.println("Screen: " + screenName);
            System.out.println("Params: " + stringParams); // Printing the ordered map
            System.out.println("Rules: " + rules);

            System.out.println("\n=== Processing Screen: " + screenName + " ===");
            System.out.println("Mode: " + mode);

            // --- Navigate to screen ---
            WebElement search = driver.findElement(By.cssSelector("input[placeholder='Search Here']"));
            search.clear();
            search.sendKeys(screenName);
            driver.findElement(By.id(screenId)).click();

            switch (mode) {
                case "UPLOAD_ONLY":
                    downloadExcel(driver, screenName, stringParams);
                    uploadFile(driver, templatePath);
                    break;

                case "UPLOAD_WITH_CHANGES":
                    String updatedFile = ExcelGen.generateExcel(templatePath, rules);
                    downloadExcel(driver, screenName, stringParams);
                    uploadFile(driver, updatedFile);
                    break;

                default:
                    System.out.println("❌ Unknown mode: " + mode);
            }

            driver.findElement(By.id("menurollin")).click();
        }

        Thread.sleep(1000);
        driver.quit();
    }

    private static void uploadFile( WebDriver driver, String filePath) throws InterruptedException {
        System.out.println("⬆ Uploading: " + filePath);
        WebElement fileInput = driver.findElement(By.cssSelector("input[type='file']"));
        fileInput.sendKeys(filePath);
        Thread.sleep(1000);
        try {
            String successMsg = driver.findElement(By.id("notify_text_success")).getText();
            System.out.println("✅ Success: " + successMsg);
        } catch (Exception e) {
            String errorMsg = driver.findElement(By.id("notify_text_error")).getText();
            System.out.println("❌ Upload Failed! " + errorMsg );
        }
    }

    private static void downloadExcel(WebDriver driver, String screenName, Map<String, String> params) throws InterruptedException {
        for (Map.Entry<String, String> field : params.entrySet()) {
            String paramId = field.getKey();
            String value = field.getValue();

            try {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                WebElement element = wait.until(ExpectedConditions.elementToBeClickable(By.id(paramId)));

                System.out.println("-> Filling field ID: [" + paramId + "] with Value: [" + value + "]");
                Thread.sleep(500);
                element.clear();
                element.sendKeys(value);
                Thread.sleep(500);

                WebElement item = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("(//div[@id='dropdown-content']//*[contains(text(), '" + value + "')])[1]")
                ));
                item.click();

            } catch (Exception e) {
                // Catching a broader exception might be safer for various wait/element issues
                System.out.println("❌ Could not process element: " + paramId + " with value " + value + ". Error: " + e.getMessage());
            }
        }

        System.out.println("⬇ Downloading Excel for: " + screenName);
        driver.findElement(By.xpath("//button[contains(text(),'Download Excel')]")).click();
        Thread.sleep(1000); // Increased sleep time slightly to ensure download starts

        try {
            String successMsg = driver.findElement(By.id("notify_text_success")).getText();
            if (successMsg.contains("File downloaded successfully")) {
                System.out.println("✅ Success: " + successMsg);
            } else {
                System.out.println("⚠️ Unexpected message: " + successMsg);
            }
        } catch (Exception e) {
            try {
                String errorMsg = driver.findElement(By.id("notify_text_error")).getText();
                System.out.println("❌ Download Failed! " + errorMsg);
            } catch (Exception ex) {
                System.out.println("❌ Could not find notification message. " + ex.getMessage());
            }
        }
    }
}
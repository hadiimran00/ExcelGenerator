//package org.example;
//
//import org.openqa.selenium.By;
//import org.openqa.selenium.WebDriver;
//import org.openqa.selenium.chrome.ChromeDriver;
//import org.openqa.selenium.chrome.ChromeOptions;
//import org.openqa.selenium.support.ui.WebDriverWait;
//import org.openqa.selenium.WebElement;
//import io.github.bonigarcia.wdm.WebDriverManager;
//
//
//import java.time.Duration;
//
//public class Main {
//    public static void main(String[] args) throws InterruptedException {
//        System.setProperty("webdriver.chrome.driver", "D:\\Automation\\chromedriver-win64\\chromedriver.exe");
//        ChromeOptions options = new ChromeOptions();
////        options.addArguments("--headless");        // Run in headless mode
////        options.addArguments("--disable-gpu");     // For Windows systems
////        options.addArguments("--window-size=1920,1080");
//        WebDriverManager.chromedriver().setup();
//        WebDriver driver = new ChromeDriver(options);
//        driver.get("https://dcodecnr2dev1.unilever.com/ngui");
//
//        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
//      //  driver.manage().window().maximize();
//        driver.findElement(By.id("a3")).sendKeys("kpo_ph");
//        driver.findElement(By.id("a4")).sendKeys("Cent@123");
//        driver.findElement(By.cssSelector("button[type='submit']")).click();
//      //  driver.findElement(By.id("menurollin")).click();
//        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
//        driver.findElement(By.cssSelector("input[placeholder='Search Here']")).sendKeys("DSR Profile Bulk Upload");
//        driver.findElement(By.id(Config.json.screens.screenname)).click();
////        driver.findElement(By.xpath("//button[contains(text(),'Download Excel')]")).click();
////        String successMsg = driver.findElement(By.id("notify_text_success")).getText();
////
////        if (successMsg.equals("File downloaded successfully")) {
////            System.out.println("✅ Excel Download Success message validated!");
////        } else {
////            System.out.println("❌ Validation failed. Found: " + successMsg);
////        }
//        String basePath = "D:\\Automation\\ExcelGenerator\\Resources\\";
//        String templatePath = basePath + "dsrProfileData.xlsx"; // path to downloaded or local template
//        String outputPath = basePath + "dsrProfileDataTemp.xlsx";        // generated Excel
//        String rulesJsonPath = basePath + "DSRProfileRules.json"; // JSON rules
//
//        ExcelGen.generateExcel(templatePath, outputPath, rulesJsonPath);
//
//
//        // --- UPLOAD GENERATED EXCEL ---
//
//        WebElement fileInput = driver.findElement(By.cssSelector("input[type='file']"));
//
//        // Upload the file by sending the path
//        fileInput.sendKeys(outputPath);;
//
//        String successMsg = driver.findElement(By.id("notify_text_success")).getText();
//
//        if (successMsg.equals("File upload successful")) {
//            System.out.println("✅ Excel Upload Success message validated!");
//        } else {
//            System.out.println("❌ Validation failed. Found: " + successMsg);
//        }
//
//        // driver.findElement(By.id("AA"));
//        System.out.println("Hello world!");
//        Thread.sleep(1000);
//        driver.quit();
//    }
//}

package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;

public class Main {

    public static void main(String[] args) throws Exception {
        // --- Setup Selenium ---
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        WebDriver driver = new ChromeDriver(options);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        driver.get("https://dcodecnr2dev1.unilever.com/ngui");
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));

        // --- Login ---
        driver.findElement(By.id("a3")).sendKeys("kpo_ph");
        driver.findElement(By.id("a4")).sendKeys("Cent@123");
        driver.findElement(By.cssSelector("button[type='submit']")).click();

        // --- Read config.json ---
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new File("D:\\Automation\\ExcelGenerator\\Resources\\Config.json"));

        for (JsonNode screen : root.get("screens")) {
            String screenName = screen.get("screenName").asText();
            String screenId = screen.get("screenId").asText();
            String mode = screen.get("mode").asText();
            JsonNode rules = screen.get("rules");
            JsonNode params = screen.get("params");

            System.out.println("\n=== Processing Screen: " + screenName + " ===");
            System.out.println("Mode: " + mode);

            // --- Navigate to screen ---
            WebElement search = driver.findElement(By.cssSelector("input[placeholder='Search Here']"));
            search.clear();
            search.sendKeys(screenName);
            driver.findElement(By.id(screenId)).click();

            String basePath = "D:\\Automation\\ExcelGenerator\\Resources\\";
            String templatePath = basePath + screenName.replaceAll(" ", "") + "_template.xlsx";
            String outputPath = basePath + screenName.replaceAll(" ", "") + "_updated.xlsx";

            switch (mode) {
                case "UPLOAD_ONLY":
                    uploadFile(driver, templatePath);
                    break;

                case "UPLOAD_WITH_CHANGES":
                    String updatedFile = ExcelGen.generateExcel(templatePath, outputPath, rules.toString());
                    uploadFile(driver, updatedFile);
                    break;

                case "DOWNLOAD_EDIT_UPLOAD":
                    String downloaded = downloadExcel(driver, screenName);
                    String updated2 = ExcelGen.generateExcel(downloaded, outputPath, rules.toString());
                    uploadFile(driver, updated2);
                    break;

                default:
                    System.out.println("❌ Unknown mode: " + mode);
            }

            // --- Validate Upload Success ---
            try {
                String successMsg = driver.findElement(By.id("notify_text_success")).getText();
                System.out.println("✅ Success: " + successMsg);
            } catch (Exception e) {
                System.out.println("⚠ Could not find success message for " + screenName);
            }
        }

        Thread.sleep(1000);
        driver.quit();
    }

    private static void uploadFile(WebDriver driver, String filePath) {
        System.out.println("⬆ Uploading: " + filePath);
        WebElement fileInput = driver.findElement(By.cssSelector("input[type='file']"));
        fileInput.sendKeys(filePath);
    }

    private static String downloadExcel(WebDriver driver, String screenName) {
        System.out.println("⬇ Downloading Excel for: " + screenName);
        driver.findElement(By.xpath("//button[contains(text(),'Download Excel')]")).click();
        // TODO: wait for actual file download handling
        return "D:\\Automation\\ExcelGenerator\\Resources\\" + screenName.replaceAll(" ", "") + "_downloaded.xlsx";
    }
}

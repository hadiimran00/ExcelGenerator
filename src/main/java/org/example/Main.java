package org.example;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.WebElement;

import java.time.Duration;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.setProperty("webdriver.chrome.driver", "D:\\Automation\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
//        options.addArguments("--headless");        // Run in headless mode
//        options.addArguments("--disable-gpu");     // For Windows systems
//        options.addArguments("--window-size=1920,1080");
        WebDriver driver = new ChromeDriver(options);
        driver.get("https://dcodecnr2dev1.unilever.com/ngui");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
      //  driver.manage().window().maximize();
        driver.findElement(By.id("a3")).sendKeys("kpo_ph");
        driver.findElement(By.id("a4")).sendKeys("Cent@123");
        driver.findElement(By.cssSelector("button[type='submit']")).click();
      //  driver.findElement(By.id("menurollin")).click();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
        driver.findElement(By.cssSelector("input[placeholder='Search Here']")).sendKeys("DSR Profile Bulk Upload");
        driver.findElement(By.id("DSR_PROFILE_DATA")).click();
//        driver.findElement(By.xpath("//button[contains(text(),'Download Excel')]")).click();
//        String successMsg = driver.findElement(By.id("notify_text_success")).getText();
//
//        if (successMsg.equals("File downloaded successfully")) {
//            System.out.println("✅ Excel Download Success message validated!");
//        } else {
//            System.out.println("❌ Validation failed. Found: " + successMsg);
//        }

        String templatePath = "C:\\Users\\Okayker\\IdeaProjects\\SelAuto\\Resources\\dsrProfileData.xlsx"; // path to downloaded or local template
        String outputPath = "C:\\Users\\Okayker\\IdeaProjects\\SelAuto\\Resources\\dsrProfileDataTemp.xlsx";        // generated Excel
        String rulesJsonPath = "C:\\Users\\Okayker\\IdeaProjects\\SelAuto\\Resources\\DSRProfileRules.json"; // JSON rules

        ExcelGen.generateExcel(templatePath, outputPath, rulesJsonPath);


        // --- UPLOAD GENERATED EXCEL ---

        WebElement fileInput = driver.findElement(By.cssSelector("input[type='file']"));

        // Upload the file by sending the path
        fileInput.sendKeys(outputPath);;

        String successMsg = driver.findElement(By.id("notify_text_success")).getText();

        if (successMsg.equals("File upload successful")) {
            System.out.println("✅ Excel Upload Success message validated!");
        } else {
            System.out.println("❌ Validation failed. Found: " + successMsg);
        }

        // driver.findElement(By.id("AA"));
        System.out.println("Hello world!");
        Thread.sleep(3000);
        driver.quit();  
    }
}
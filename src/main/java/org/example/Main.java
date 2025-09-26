package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import org.openqa.selenium.WebDriver;


public class Main {

    public static int upTestPassed = 0;
    public static int upTestFailed = 0;
    public static int downTestPassed = 0;
    public static int downTestFailed = 0;


    public static void main(String[] args) throws Exception {
        WebDriverManager.chromedriver().setup();

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, String>> users = mapper.readValue(
                new File("Resources/users.json"),
                new TypeReference<>() {}
        );

        for (Map<String, String> user : users) {
            String username = user.get("username");
            String password = user.get("password");
            String url = user.get("url");
            String config = user.get("configPath");
            String resourcesFolder = user.get("resourcesFolder");
            String configPath = resourcesFolder + "\\" + config;


            System.out.println("\n=== Test Running for User: " + username + " ===");
            ChromeOptions options = new ChromeOptions();
            //Reading from applicatiom.properties
            Properties properties = new Properties();
            try (FileInputStream fis = new FileInputStream("Resources/application.properties")) {
                properties.load(fis);
            }
            String relativeDownloadDir = properties.getProperty("downloadedExcels", "DownloadedExcels");
            String downloadDir = Paths.get(System.getProperty("user.dir"), relativeDownloadDir)
                    .toAbsolutePath()
                    .toString();

            HashMap<String, Object> chromePrefs = new HashMap<>();
            chromePrefs.put("download.default_directory", downloadDir);
            chromePrefs.put("profile.default_content_setting_values.automatic_downloads", 1);
            options.setExperimentalOption("prefs", chromePrefs);

            // Get the 'headless' property
            boolean isHeadless = Boolean.parseBoolean(properties.getProperty("selenium.headless", "false"));

            if(isHeadless) {

                //For headless
                options.addArguments("--headless=new"); // Uncomment to run without opening a browser window
                options.addArguments("--window-size=1920,1080");
                options.addArguments("--disable-gpu");
                options.addArguments("--no-sandbox");

// Set a larger window size for headless mode
                options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36");
                WebDriver driver = new ChromeDriver(options);
                driver.manage().window().setSize(new Dimension(1920, 1080));
            }

            WebDriver driver = new ChromeDriver(options);
            driver.manage().window().maximize();
            driver.manage().window().setSize(new Dimension(1920, 1080));
            driver.get(url);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            // --- Login ---
            driver.findElement(By.id("a3")).sendKeys(username);
            driver.findElement(By.id("a4")).sendKeys(password);
            driver.findElement(By.cssSelector("button[type='submit']")).click();

            List<Map<String, Object>> screens = ExcelLoader.loadScreens(configPath);

            for (Map<String, Object> screen : screens) {
                String execute = (String) screen.get("execute");

                if (execute == null) {
                    execute = "";
                }
                String screenName = (String) screen.get("screenName");
                String screenId = (String) screen.get("screenId");
                String mode = (String) screen.get("mode");
                String templatePath = (String) screen.get("templatePath");
                String rootPath = System.getProperty("user.dir") +"\\"+resourcesFolder ;
                templatePath = Paths.get(rootPath, templatePath).toString();


                if (execute.isBlank() || execute.equalsIgnoreCase("y")) {
                    

                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = (Map<String, Object>) screen.get("params");
                    @SuppressWarnings("unchecked")
                    Map<String, Map<String, Object>> rules = (Map<String, Map<String, Object>>) screen.get("rules");

                    // Convert to Map<String, String> while preserving order
                    Map<String, String> stringParams = new LinkedHashMap<>();
                    if (params != null) {
                        stringParams = params.entrySet()
                                .stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        e -> String.valueOf(e.getValue()),
                                        (oldValue, newValue) -> oldValue,
                                        LinkedHashMap::new
                                ));
                    }

                    // check and open the search menu if needed
                    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

                    try {
                        // Try to find the search input
                        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("input[placeholder='Search Here']")));
                    } catch (TimeoutException e) {
                        // If not found, click the menu and retry
                        wait.until(ExpectedConditions.elementToBeClickable(By.id("menurollin"))).click();
                        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("input[placeholder='Search Here']")));
                    }



                    System.out.println("\n==============================================================");
                    System.out.println("          🚀 Processing Screen: " + screenName + " 🚀          ");
                    System.out.println("==============================================================\n");

                    System.out.println("Mode: " + mode);

                    // Navigate to the screen
                    WebElement search = driver.findElement(By.cssSelector("input[placeholder='Search Here']"));
                    search.clear();
                    search.sendKeys(screenName);
                    Thread.sleep(500);

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

                        case "DOWNLOAD_ONLY":
                            downloadExcel(driver, screenName, stringParams);
                            break;

                        default:
                            System.out.println("❌ Unknown mode: " + mode);
                    }

                    driver.findElement(By.id("menurollin")).click();
                } else {
                    System.out.println("⏩ Skipping screen: " + screenName + " (execute=" + execute + ")");
                }
            }

            driver.quit();
        }
        try (PrintWriter writer = new PrintWriter("summary.txt")) {
            writer.println("=== Test Finished ===");
            writer.println("Upload Tests Passed  ---> " + upTestPassed);
            writer.println("Upload Tests Failed  ---> " + upTestFailed);
            writer.println("Download Tests Passed --> " + downTestPassed);
            writer.println("Download Tests Failed --> " + downTestFailed);
        }

// Append a general run completion note
        try (FileWriter writer = new FileWriter("summary.txt", true)) {
            writer.write("Automation run completed successfully!\n");
        }


    }


    private static void uploadFile(WebDriver driver, String filePath) throws InterruptedException {

        System.out.println("⬆ Uploading: " + filePath);
        WebElement fileInput = driver.findElement(By.cssSelector("input[type='file']"));
        fileInput.sendKeys(filePath);
        Thread.sleep(500);

        // Wait for notification instead of sleeping
       // WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

// Check for success message
        List<WebElement> successMsgList = driver.findElements(By.id("notify_text_success"));
        if (!successMsgList.isEmpty() && successMsgList.get(0).isDisplayed()) {
            WebElement successMsg = successMsgList.get(0);
            System.out.println("✅ Success: " + successMsg.getText());
            upTestPassed++;

        } else {
            upTestFailed++;
            // Check for error message

            List<WebElement> errorMsgList = driver.findElements(By.id("notify_text_error"));
            if (!errorMsgList.isEmpty() && errorMsgList.get(0).isDisplayed()) {
                WebElement errorMsg = errorMsgList.get(0);
                System.out.println("❌ Upload Failed! " + errorMsg.getText());
            } else {
                System.out.println("❌ Could not find notification message.");
            }
        }

    }

    private static void downloadExcel(WebDriver driver, String screenName, Map<String, String> params) throws InterruptedException, IOException {
        for (Map.Entry<String, String> field : params.entrySet()) {
            String paramId = field.getKey();
            String value = field.getValue();
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

            try {
                WebElement element = wait.until(ExpectedConditions.elementToBeClickable(By.id(paramId)));

                System.out.println("-> Filling field ID: [" + paramId + "] with Value: [" + value + "]");
                Thread.sleep(500);
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
                        System.out.println("⚠️ No dropdown item found for: " + value + " (This may be okay for non-dropdown fields)");
                    }
                }

            } catch (Exception e) {
                System.out.println("❌ Could not process element: " + paramId + " with value " + value + ". Error: " + e.getMessage());
            }
        }

        System.out.println("⬇ Downloading Excel for: " + screenName);
        Thread.sleep(1000);
        File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        FileUtils.copyFile(screenshot, new File("D:\\JAR\\Resources\\screenshot.png"));

        WebElement downloadBtn = driver.findElement(By.xpath("//button[contains(text(),'Download Excel')]"));

        // Move to element to scroll into view only if needed
        Actions actions = new Actions(driver);
        actions.moveToElement(downloadBtn).perform();

        downloadBtn.click();

        // Wait a bit for messages to appear
        Thread.sleep(1000);

        List<WebElement> successMsgList = driver.findElements(By.id("notify_text_success"));
        if (!successMsgList.isEmpty() && successMsgList.get(0).isDisplayed()) {
            WebElement successMsg = successMsgList.get(0);
            String text = successMsg.getText();
            if (text.contains("File downloaded successfully")) {
                System.out.println("✅ Success: " + text);
                downTestPassed++;

            } else {
                System.out.println("⚠️ Unexpected message: " + text);
            }
        } else {
            downTestFailed++;
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
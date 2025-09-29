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
import java.util.NoSuchElementException;
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
        try (PrintWriter writer = new PrintWriter("summary.txt")) {
            writer.print(""); // This will empty the file
        }



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

            if (isHeadless) {

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
            try {
                driver.findElement(By.id("a3")).sendKeys(username);
                driver.findElement(By.id("a4")).sendKeys(password);
                driver.findElement(By.cssSelector("button[type='submit']")).click();
            } catch (Exception e) {
                takeScreenshot(driver);
                throw new RuntimeException(e);
            }
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
                String rootPath = System.getProperty("user.dir") + "\\" + resourcesFolder;
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
                        // First attempt: directly wait for search input
                        wait.until(ExpectedConditions.visibilityOfElementLocated(
                                By.cssSelector("input[placeholder='Search Here']")));
                    } catch (TimeoutException e1) {
                        try {
                            // Second attempt: click menu and retry
                            wait.until(ExpectedConditions.elementToBeClickable(By.id("menurollin"))).click();
                            wait.until(ExpectedConditions.visibilityOfElementLocated(
                                    By.cssSelector("input[placeholder='Search Here']")));
                        } catch (TimeoutException e2) {
                            // If both fail → take screenshot
                            takeScreenshot(driver);
                            throw e2; // rethrow so test fails
                        }
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

                    //  driver.findElement(By.id("menurollin")).click();
                    robustClick(driver, By.id("menurollin"));
                } else {
                    System.out.println("⏩ Skipping screen: " + screenName + " (execute=" + execute + ")");
                }
            }

            driver.quit();

            // writing summary
            try (PrintWriter writer = new PrintWriter(new FileWriter("summary.txt", true))) {
                writer.println("=== Test Finished for User: " + username + " ===");
                writer.println("Upload Tests Total  -----> " + (upTestPassed + upTestFailed));
                writer.println("Upload Tests Passed ---> " + upTestPassed);
                writer.println("Upload Tests Failed ---> " + upTestFailed);
                writer.println("Download Tests Total -----> " + (downTestPassed + downTestFailed));
                writer.println("Download Tests Passed --> " + downTestPassed);
                writer.println("Download Tests Failed --> " + downTestFailed);
                writer.println();
            }
            //for resetting old values
            upTestPassed = 0;
            upTestFailed = 0;
            downTestPassed = 0;
            downTestFailed = 0;


        }

// Append a general run completion note
        try (FileWriter writer = new FileWriter("summary.txt", true)) {
            writer.write("\n Automation run completed successfully!\n");
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
        if (!successMsgList.isEmpty() && successMsgList.get(0).isDisplayed() && successMsgList.get(0).getText().contains("File upload successful")) {
            WebElement successMsg = successMsgList.get(0);
            System.out.println("✅ Success: " + successMsg.getText());
            upTestPassed++;

        } else {
            upTestFailed++;
            // Check for error message
            takeScreenshot(driver);
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
                robustClick(driver, By.id("downloadExcel"));
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }


        // Move to element to scroll into view only if needed
//        Actions actions = new Actions(driver);
//        actions.moveToElement(downloadBtn).perform();
//
//        downloadBtn.click();

        // Wait a bit for messages to appear
        Thread.sleep(1000);

        List<WebElement> successMsgList = driver.findElements(By.id("notify_text_success"));
        if (!successMsgList.isEmpty() && successMsgList.get(0).isDisplayed() && successMsgList.get(0).getText().contains("File downloaded successfully") ) {
            WebElement successMsg = successMsgList.get(0);
            String text = successMsg.getText();
                System.out.println("✅ Success: " + text);
                downTestPassed++;


        } else {
            downTestFailed++;
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


    /**
     * Waits for an element to be clickable, scrolls it into view, and uses a JS click as a fallback.
     * This is highly robust for headless and CI/CD environments.
     */
    private static void robustClick(WebDriver driver, By locator) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        try {
            WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
            element.click();
        } catch (Exception e) {
            //System.out.println("⚠️ Standard click failed, attempting JS click for locator: " + locator);
            try {
                WebElement element = driver.findElement(locator); // Re-find to avoid stale element
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            } catch (Exception jsException) {
                takeScreenshot(driver);
                System.out.println("❌ Both standard and JS click failed for locator: " + locator);
                throw jsException; // Re-throw the exception to fail the test
            }
        }
    }


    /**
     * Takes a screenshot on failure and saves it to a 'screenshots' directory.
     */
    private static void takeScreenshot(WebDriver driver) {
        File screenshotsDir = new File("screenshots");
        if (!screenshotsDir.exists()) {
            screenshotsDir.mkdirs();
        }
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS").format(new Date());

        String fileName = "ErrorScreenshot_" + timestamp + ".png";
        try {
            File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            FileUtils.copyFile(screenshotFile, new File(screenshotsDir, fileName));
            System.out.println("📸 Screenshot captured: " + fileName);
        } catch (Exception e) {
            System.out.println("❌ Failed to take screenshot: " + e.getMessage());
        }
    }


}
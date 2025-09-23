package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {

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
           // options.addArguments("--headless=new"); // Uncomment to run without opening a browser window
            WebDriver driver = new ChromeDriver(options);

            driver.manage().window().maximize();
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
                    System.out.println("\n ▶ Executing Screen: " + screenName);

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

                    // Robustly check and open the search menu if needed
                    try {
                        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                        wait.until(ExpectedConditions.visibilityOfElementLocated(
                                By.cssSelector("input[placeholder='Search Here']")));
                    } catch (TimeoutException e) {
                        driver.findElement(By.id("menurollin")).click();
                        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                        wait.until(ExpectedConditions.visibilityOfElementLocated(
                                By.cssSelector("input[placeholder='Search Here']")));
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
    }
    public int upTestPassed = 0;
    public int upTestFailed = 0;

    private static void uploadFile(WebDriver driver, String filePath) throws InterruptedException {

        System.out.println("⬆ Uploading: " + filePath);
        WebElement fileInput = driver.findElement(By.cssSelector("input[type='file']"));
        fileInput.sendKeys(filePath);
        Thread.sleep(500);

        // Wait for notification instead of sleeping
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

// Check for success message
        List<WebElement> successMsgList = driver.findElements(By.id("notify_text_success"));
        if (!successMsgList.isEmpty() && successMsgList.get(0).isDisplayed()) {
            WebElement successMsg = successMsgList.get(0);
            System.out.println("✅ Success: " + successMsg.getText());

        } else {
            // Check for error message

            List<WebElement> errorMsgList = driver.findElements(By.id("notify_text_error"));
            if (!errorMsgList.isEmpty() && errorMsgList.get(0).isDisplayed()) {
                WebElement errorMsg = errorMsgList.get(0);
                System.out.println("❌ Download Failed! " + errorMsg.getText());
            } else {
                System.out.println("❌ Could not find notification message.");
            }
        }

    }

    private static void downloadExcel(WebDriver driver, String screenName, Map<String, String> params) throws InterruptedException {
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

            } else {
                System.out.println("⚠️ Unexpected message: " + text);
            }
        } else {

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
package org.example.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.logging.log4j.Logger;
import org.example.ui.utilities.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static org.example.ui.utilities.ScreenshotService.takeScreenshot;
import static org.example.ui.utilities.TestSummary.*;

public class Main {
    private static final Logger logger = LoggerUtil.getLogger(Main.class);

    public static void main(String[] args) throws Exception {

        DirectoryCleaner.cleanFolders();
        clearSummaryFile();
        WebDriverManager.chromedriver().setup();

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, String>> users = mapper.readValue(
                new File("users.json"),
                new TypeReference<>() {}
        );

        try (PrintWriter writer = new PrintWriter("summary.txt")) {
            writer.print(""); // clear summary
        }

        // === Loop through all users safely ===
        for (Map<String, String> user : users) {
            WebDriver driver = null;
            String username = null;
            try {
                username = user.get("username");
                String password = user.get("password");
                String url = user.get("url");
                String config = user.get("configPath");
                String resourcesFolder = user.get("resourcesFolder");
                String configPath = resourcesFolder + "\\" + config;
                String country = user.get("country");
                String executeUser = user.get("execute");

                if (executeUser.equalsIgnoreCase("no")) {
                    logger.info(" ");
                    logger.info("⏩ Skipping user: {} (execute= NO)", username);
                    continue;
                }

                // === Setup Chrome options ===
                ChromeOptions options = new ChromeOptions();
                Properties properties = new Properties();
                try (FileInputStream fis = new FileInputStream("application.properties")) {
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

                boolean isHeadless = Boolean.parseBoolean(properties.getProperty("selenium.headless", "false"));
                if (isHeadless) {
                    options.addArguments("--headless=new");
                    options.addArguments("--window-size=1920,1080");
                    options.addArguments("--disable-gpu");
                      options.addArguments("--no-sandbox");
                    options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36");
                }

                driver = new ChromeDriver(options);
                driver.manage().window().setSize(new Dimension(1920, 1080));
                driver.get(url);
                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));

                logger.info("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
                logger.info("🔍 Running Tests for User: {} | Country: {}", username, country);
                logger.info("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");

                // === Login ===
                try {
                    driver.findElement(By.id("a3")).sendKeys(username);
                    driver.findElement(By.id("a4")).sendKeys(password);
                    driver.findElement(By.cssSelector("button[type='submit']")).click();
                } catch (Exception e) {
                    takeScreenshot(driver);
                    throw new RuntimeException("Login failed for user: " + username, e);
                }

                List<Map<String, Object>> screens = ExcelLoader.loadScreens(configPath);

                for (Map<String, Object> screen : screens) {
                    String execute = (String) screen.getOrDefault("execute", "");
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

                        Map<String, String> stringParams = new LinkedHashMap<>();
                        if (params != null) {
                            stringParams = params.entrySet().stream()
                                    .collect(Collectors.toMap(
                                            Map.Entry::getKey,
                                            e -> String.valueOf(e.getValue()),
                                            (oldValue, newValue) -> oldValue,
                                            LinkedHashMap::new
                                    ));
                        }

                        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                        try {
                            wait.until(ExpectedConditions.visibilityOfElementLocated(
                                    By.cssSelector("input[placeholder='Search Here']")));
                        } catch (TimeoutException e1) {
                            try {
                                wait.until(ExpectedConditions.elementToBeClickable(By.id("menurollin")));
                                Event.robustClick(driver, By.id("menurollin"));
                                wait.until(ExpectedConditions.visibilityOfElementLocated(
                                        By.cssSelector("input[placeholder='Search Here']")));
                            } catch (TimeoutException e2) {
                                takeScreenshot(driver);
                                throw e2;
                            }
                        }

                        logger.info("==============================================================");
                        logger.info("🚀 Processing Screen: {}", screenName);
                        logger.info("==============================================================");
                        logger.info("Mode: {}", mode);

                        WebElement search = driver.findElement(By.cssSelector("input[placeholder='Search Here']"));
                        search.clear();
                        search.sendKeys(screenName);
                        Thread.sleep(500);
                        String updatedFile;

                        driver.findElement(By.id(screenId)).click();

                        switch (mode) {
                            case "DOWNLOAD_UPLOAD":
                                FileManager.downloadExcel(driver, screenName, stringParams);
                                FileManager.uploadFile(driver, screenName, templatePath);
                                break;

                            case "DOWNLOAD_UPDATE_UPLOAD":
                                updatedFile = ExcelGen.generateExcel(templatePath, rules);
                                FileManager.downloadExcel(driver, screenName, stringParams);
                                FileManager.uploadFile(driver, screenName, updatedFile);
                                break;

                            case "DOWNLOAD_ONLY":
                                FileManager.downloadExcel(driver, screenName, stringParams);
                                break;

                            case "UPLOAD_ONLY":
                                FileManager.uploadFile(driver, screenName, templatePath);
                                break;

                            case "PEP":
                                //special case for Product exclusion policy
                                Event.robustClick(driver, By.id("row_1_description"));
                                FileManager.downloadExcel(driver, screenName, stringParams);
                                FileManager.uploadFile(driver, screenName, templatePath);
                                break;

                            case "LMT":
                                //special case for LMT/order bulk upload
                                updatedFile = ExcelGen.generateExcel(templatePath, rules);
                                FileManager.downloadExcel(driver, screenName, stringParams);
                                FileManager.uploadFile(driver, screenName, updatedFile);
                                validateCashmemo.validateCashmemoForLMT(driver, wait);
                                break;

                            default:
                                logger.info("❌ Unknown mode: {}", mode);
                        }

                        Event.robustClick(driver, By.id("menurollin"));
                    } else {
                        logger.info("⏩ Skipping screen: {} (execute={})", screenName, execute);
                    }
                }

                // Write summary after each user
                writeTestSummary(country);
                resetTestCounter();

            } catch (Exception e) {
                logger.error("💥 Unexpected error for user: {} | Message: {}", username, e.getMessage(), e);
                if (driver != null) takeScreenshot(driver);
            } finally {
                if (driver != null) {
                    try {
                        driver.quit();
                    } catch (Exception ignored) {
                    }
                }
            }
        } // end for loop

        // Run once after all users
        closeSummaryHtml();
        logger.info("=== ✅ Test Run Completed Successfully! ===");
    }
}

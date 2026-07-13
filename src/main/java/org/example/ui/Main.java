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

import static org.example.ui.utilities.LoaderWait.waitForLoaderToDisappear;
import static org.example.ui.utilities.PJPExcelUpload.findScreenByTestId;
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
                String orga = user.get("orga");
                String dist=user.get("dist");

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

                boolean isHeadless = Boolean.parseBoolean(
                        properties.getProperty("selenium.headless", "false"));

                if (isHeadless) {
                    options.addArguments("--headless=new");
                    options.addArguments("--window-size=1920,1080");
                    options.addArguments("--disable-gpu");
                    options.addArguments("--no-sandbox");
                    options.addArguments("--disable-dev-shm-usage");
                }
                String zoom =properties.getProperty("zoom");
                options.addArguments("--force-device-scale-factor=0."+zoom);

                driver = new ChromeDriver(options);

                if (isHeadless) {
                    driver.manage().window().setSize(new Dimension(1920, 1080));
                } else {
                    driver.manage().window().maximize();
                }

                driver.get(url);
                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));


                logger.info("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
                logger.info("🔍 Running Tests for User: {} | Country: {}", username, country);
                logger.info("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                // === Login ===
                try {
                    driver.findElement(By.id("a3")).sendKeys(username);
                    driver.findElement(By.id("a4")).sendKeys(password);
                    driver.findElement(By.cssSelector("button[type='submit']")).click();
                    waitForLoaderToDisappear(driver);


                    List<WebElement> selectBoxes = driver.findElements(By.id("selectBox1"));

                    if (!selectBoxes.isEmpty() && selectBoxes.get(0).isDisplayed() && orga != null) {
                        driver.findElement(By.id("selectBox1")).sendKeys(orga);
                        try {
                            WebElement item = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("(//div[@id='dropdown-content']//*[contains(text(), '" + orga + "')])[1]")));
                            item.click();
                        } catch (TimeoutException e) {
                            takeScreenshot(driver,"Login");
                            logger.info("⚠️ No dropdown item found for: {} (This may be data issue. Please check your users file.)", orga);
                        }
                        driver.findElement(By.id("proceedBtn")).click();
                        Thread.sleep(500);

                    }
                    waitForLoaderToDisappear(driver);

                    if (!selectBoxes.isEmpty() && dist != null) {
                        driver.findElement(By.id("selectBox1")).sendKeys(dist);
                        try {
                            WebElement item = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("(//div[@id='dropdown-content']//*[contains(text(), '" + dist + "')])[1]")));
                            item.click();
                        } catch (TimeoutException e) {
                            takeScreenshot(driver,"Login");
                            logger.info("⚠️ No dropdown item found for: {} (This may be data issue. Please check your users file.)", dist);
                        }

                        Event.robustClick(driver,By.id("proceedBtn"));
                        Thread.sleep(500);

                    }
                    waitForLoaderToDisappear(driver);
                } catch (Exception e) {
                    takeScreenshot(driver,"Login");
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
//              

                    FileManager.setResourceFolder(rootPath);

                    templatePath = Paths.get(rootPath, templatePath).toString();
                    String testID = (String) screen.get("testID");
                    String validateMode=(String) screen.get("validateMode");
                    Map<String, Map<String, Object>> validations =
                            (Map<String, Map<String, Object>>) screen.get("validations");

                    if (validations == null) {
                        validations = Collections.emptyMap();
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, String> scenarioData =
                            (Map<String, String>) screen.getOrDefault(
                                    "scenarioData",
                                    Map.of()
                            );
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

                   //     WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                        waitForLoaderToDisappear(driver);
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
                                takeScreenshot(driver,"Menu");
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
                                TestSummary.appendValidation(PostUploadValidator.run(driver,validations.get(testID), testID, templatePath, downloadDir, validateMode,scenarioData));
                                break;

                            case "DOWNLOAD_UPDATE_UPLOAD":
                                updatedFile = ExcelGen.generateExcel(templatePath, rules, testID);
                                FileManager.uploadFile(driver, screenName, updatedFile);
                                FileManager.downloadExcel(driver, screenName, stringParams);
                            //    PostUploadValidator.run(driver,validations.get(testID), testID, updatedFile, downloadDir);
                                TestSummary.appendValidation(PostUploadValidator.run(driver, validations.get(testID), testID, updatedFile, downloadDir,validateMode,scenarioData));
                                break;

                            case "DOWNLOAD_ONLY":
                                FileManager.downloadExcel(driver, screenName, stringParams);
                                TestSummary.appendValidation(PostUploadValidator.run(driver, validations.get(testID), testID, "", downloadDir,validateMode,scenarioData));
                                break;

                            case "UPLOAD_ONLY":
                                FileManager.uploadFile(driver, screenName, templatePath);
                             TestSummary.appendValidation(PostUploadValidator.run(driver,validations.get(testID), testID, templatePath, downloadDir, validateMode,scenarioData));
                                ValidationResult result =
                                        PostUploadValidator.run(
                                                driver,
                                                validations.get(testID),
                                                testID,
                                                templatePath,
                                                downloadDir,
                                                validateMode,
                                                scenarioData);



                                break;


                            case "PEP":
                                //special case for Product exclusion policy
                                Event.robustClick(driver, By.id("row_1_description"));
                                FileManager.downloadExcel(driver, screenName, stringParams);
                                FileManager.uploadFile(driver, screenName, templatePath);
                                break;

                            case "PJP": {
                                // scenarioData for the PJP row itself carries the three testIds
                                // that tell us which rows in the Excel-config sheet are the
                                // DSR / Header / Detail screens for this flow.
                                String dsrTestId = scenarioData.get("DSRTestId");
                                String headerTestId = scenarioData.get("PJPHeaderTestId");
                                String detailTestId = scenarioData.get("PJPConfigTestId");

                                Map<String, Object> dsrScreen =
                                        findScreenByTestId(screens, dsrTestId, resourcesFolder);
                                Map<String, Object> headerScreen =
                                        findScreenByTestId(screens, headerTestId, resourcesFolder);
                                Map<String, Object> detailScreen =
                                        findScreenByTestId(screens, detailTestId, resourcesFolder);

                                ValidationResult pjpResult = PJPExcelUpload.run(
                                        driver,
                                        dsrScreen,
                                        headerScreen,
                                        detailScreen,
                                        scenarioData,
                                        downloadDir
                                );

                                TestSummary.appendValidation(pjpResult);
                                break;
                            }
                            case "DOWNLOAD_MODIFY_UPLOAD": {

                                // Download latest file from application
                                FileManager.downloadExcel(driver, screenName, stringParams);

                                // Find downloaded file
                                String downloadedFile = FileManager.getLatestDownloadedFile(downloadDir);

                                // Update downloaded excel
                                String updatedDownloadedFile =
                                        ExcelGen.generateExcel(downloadedFile, rules, testID);

                                // Upload modified file
                                FileManager.uploadFile(driver, screenName, updatedDownloadedFile);

                                // Validate
                                TestSummary.appendValidation(PostUploadValidator.run(
                                        driver,
                                        validations.get(testID),
                                        testID,
                                        updatedDownloadedFile,
                                        downloadDir,
                                        validateMode,
                                        scenarioData));

                                break;
                            }
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
                logger.info("💥 Unexpected error for user: {} | Message: {}", username, e.getMessage(), e);
                if (driver != null) takeScreenshot(driver,username);
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

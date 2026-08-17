
package org.example.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.logging.log4j.Logger;
import org.example.ui.pages.loginPage;
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

import static org.example.ui.pages.basePage.findScreenByTestId;
import static org.example.ui.utilities.LoaderWait.waitForLoaderToDisappear;
import static org.example.ui.utilities.ScreenshotService.takeScreenshot;
import static org.example.ui.utilities.TestSummary.*;

public class Main {
    private static final Logger logger = LoggerUtil.getLogger(Main.class);
    private static String resourceFolder;
    public static Map<String, String> currentUser;
    public static void setResourceFolder(String folder) {
        resourceFolder = folder;
    }

    public static String getResourceFile(String fileName) {
        return new File(resourceFolder, fileName).getAbsolutePath();
    }


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
            String country = null;
            boolean testExecuted = false;

            try {
                currentUser = user;
                username = user.get("username");
                String password = user.get("password");
                String url = user.get("url");
                String config = user.get("configPath");
                String resourcesFolder = user.get("resourcesFolder");
                String configPath = resourcesFolder + "\\" + config;
                country = user.get("country");
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
                loginPage loginPage = new loginPage(driver);
                loginPage.login(username, password, orga, dist);


                List<Map<String, Object>> screens = ExcelLoader.loadScreens(configPath);

                for (Map<String, Object> screen : screens) {
                    String execute = (String) screen.getOrDefault("execute", "");
                    String screenName = (String) screen.get("screenName");
                    String screenId = (String) screen.get("screenId");
                    String mode = (String) screen.get("mode");
                    String templatePath = (String) screen.get("templatePath");
                    String rootPath = System.getProperty("user.dir") + "\\" + resourcesFolder;
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
                        testExecuted = true;
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
                                FileManager.uploadFile(driver, screenName, templatePath);
                                FileManager.downloadExcel(driver, screenName, stringParams);
                                TestSummary.appendValidation(PostUploadValidator.run(driver,validations.get(testID), testID, templatePath, downloadDir, validateMode,scenarioData));
                                TestDataCleanup(scenarioData, driver);
                                break;

                            case "DOWNLOAD_UPDATE_UPLOAD":
                                updatedFile = ExcelGen.generateExcel(templatePath, rules, testID);
                                FileManager.uploadFile(driver, screenName, updatedFile);
                                FileManager.downloadExcel(driver, screenName, stringParams);
                            //    PostUploadValidator.run(driver,validations.get(testID), testID, updatedFile, downloadDir);
                                TestSummary.appendValidation(PostUploadValidator.run(driver, validations.get(testID), testID, updatedFile, downloadDir,validateMode,scenarioData));
                                TestDataCleanup(scenarioData, driver);
                                break;


                            case "DOWNLOAD_ONLY":
                                FileManager.downloadExcel(driver, screenName, stringParams);
                                TestSummary.appendValidation(PostUploadValidator.run(driver,validations.get(testID), testID, templatePath, downloadDir, validateMode,scenarioData));
                                break;

                            case "UPLOAD_ONLY":
                                FileManager.uploadFile(driver, screenName, templatePath);
                             TestSummary.appendValidation(PostUploadValidator.run(driver,validations.get(testID), testID, templatePath, downloadDir, validateMode,scenarioData));
                                TestDataCleanup(scenarioData, driver);
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

                                TestDataCleanup(scenarioData, driver);
                                break;
                            }
                            case "LOCUS_UP": {
                                // 1. Extract direct values from your scenarioData
                                String bulkOrderUploadTestID = scenarioData.get("bulkOrderUploadTestID");
                                String deliverScreenId = scenarioData.get("DeliverScreenId");
                                String deliveryScreenName = scenarioData.get("DeliveryScreenName");
                                String transScreenId = scenarioData.get("TransscreenId");
                                String transScreenName = scenarioData.get("TransscreenName");
                                String locusUploadTestId = scenarioData.get("LocusUploadTestId");

                                // --- Defensive Checks ---
                                if (bulkOrderUploadTestID == null || bulkOrderUploadTestID.isBlank()) {
                                    throw new IllegalArgumentException("❌ 'bulkOrderUploadTestID' is missing in scenarioData for Row " + testID);
                                }
                                if (deliverScreenId == null || deliverScreenId.isBlank()) {
                                    throw new IllegalArgumentException("❌ 'DeliverScreenId' (DYL_201080) is missing in scenarioData for Row " + testID);
                                }
                                if (transScreenId == null || transScreenId.isBlank()) {
                                    throw new IllegalArgumentException("❌ 'TransscreenId' (DYL_BG1016) is missing in scenarioData for Row " + testID);
                                }

                                // --- Look up screens that have dedicated config sheets ---
                                // This finds the Bulk Upload Screen (Test ID: 04)
                                Map<String, Object> bulkOrderScreen =
                                        findScreenByTestId(screens, bulkOrderUploadTestID.trim(), resourcesFolder);

                                // This finds the Locus Upload Screen (Test ID: 26, using parent loop fallback)
                                String targetLocusTestId = (locusUploadTestId != null && !locusUploadTestId.isBlank())
                                        ? locusUploadTestId.trim() : testID;
                                Map<String, Object> locusUploadScreen =
                                        findScreenByTestId(screens, targetLocusTestId, resourcesFolder);

                                // --- Construct Screen Maps on the fly ---
                                // We package these directly so LocusUploadFlow.run receives the Map structure it expects
                                Map<String, Object> deliveryDateChangeScreen = new HashMap<>();
                                deliveryDateChangeScreen.put("screenId", deliverScreenId.trim());
                                deliveryDateChangeScreen.put("screenName", deliveryScreenName != null ? deliveryScreenName.trim() : "delivery date change");

                                Map<String, Object> transactionInquiryScreen = new HashMap<>();
                                transactionInquiryScreen.put("screenId", transScreenId.trim());
                                transactionInquiryScreen.put("screenName", transScreenName != null ? transScreenName.trim() : "BG - Transaction Inquiry");

                                // --- Run Flow ---
                                ValidationResult locusResult = LocusUploadFlow.run(
                                        driver,
                                        bulkOrderScreen,
                                        deliveryDateChangeScreen, // Sent as a map
                                        locusUploadScreen,
                                        transactionInquiryScreen,  // Sent as a map
                                        scenarioData,
                                        downloadDir
                                );

                                TestSummary.appendValidation(locusResult);
                                break;
                            }
                            case "STOCK_RECON":
                                ValidationResult result = new ValidationResult("Stock Reconciliation [" + testID + "]");

                                try {
                                    // 1. Trigger template download
                                    FileManager.downloadExcel(driver, screenName, stringParams);

                                    // 2. Fetch file path safely (prevents NullPointerException on new File())
                                    String downloadedFilePath = FileManager.getLatestDownloadedFile(downloadDir);

                                    if (downloadedFilePath == null || downloadedFilePath.isBlank()) {
                                        result.fail("Stock Reconciliation download failed for screen [" + screenName + "]: No downloaded file path returned.");
                                    } else {
                                        File downloadedFile = new File(downloadedFilePath);

                                        if (!downloadedFile.exists() || !downloadedFile.isFile() || downloadedFile.length() == 0) {
                                            result.fail("Stock Reconciliation download failed for screen [" + screenName + "]: File does not exist or is empty at path [" + downloadedFilePath + "].");
                                        } else {
                                            // 3. Execute with the matching 5-parameter streamlined signature
                                            result = PhysicalStockReconciliationFlow.run(
                                                    driver,
                                                    downloadedFile,
                                                    testID,
                                                    scenarioData,
                                                    downloadDir
                                            );
                                        }
                                    }
                                } catch (Exception e) {
                                    logger.error("❌ Stock Reconciliation step failed for testID [{}]", testID, e);
                                    result.fail("Stock Reconciliation Flow failed due to download exception: " + e.getMessage());
                                }

                                // Process or pass 'result' forward to your master execution log/collector
                                // 4. Log validation outcomes and break execution safely
                                TestSummary.appendValidation(result);
                                break;
//                            case "PARTIAL_RETURN_BUDGET": {
//                                ValidationResult budgetResult = PartialReturnBudgetFlow.run(
//                                        driver,
//                                        scenarioData,
//                                        downloadDir
//                                );
//
//                                TestSummary.appendValidation(budgetResult);
//                                break;
//                            }
                            default:
                                logger.info("❌ Unknown mode: {}", mode);
                        }

                        Event.robustClick(driver, By.id("menurollin"));
                    } else {
                        logger.info("⏩ Skipping screen: {} (execute={})", screenName, execute);
                    }
                }

                // Write summary after each user
//                writeTestSummary(country);
//                resetTestCounter();

                } catch (Exception e) {
                logger.info("💥 Unexpected error for user: {} | Message: {}", username, e.getMessage(), e);
                if (driver != null) takeScreenshot(driver,username);
                } finally {
                try {
                if (country != null && testExecuted) {
                    writeTestSummary(country);
                    resetTestCounter();
                }
            } catch (Exception ex) {
                logger.error("Unable to write summary", ex);
            }

                if (driver != null) {
                    driver.quit();
                }
//                if (driver != null) {
//                    try {
//                        driver.quit();
//                    } catch (Exception ignored) {
//                    }
//                }
            }
        } // end for loop

        // Run once after all users
        closeSummaryHtml();
        logger.info("=== ✅ Test Run Completed Successfully! ===");
    }

    private static void TestDataCleanup(Map<String, String> scenarioData, WebDriver driver) throws InterruptedException {
        String testDataExcel = scenarioData.get("testDataExcel");

        if (testDataExcel != null && !testDataExcel.isBlank()) {
            FileManager.uploadFileSilent(
                    driver,
                    FileManager.getResourceFile(testDataExcel)
            );
        }
    }
}

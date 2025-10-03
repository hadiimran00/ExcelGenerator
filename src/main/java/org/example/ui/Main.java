package org.example.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.*;
import org.example.ui.utilities.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import org.openqa.selenium.WebDriver;

import static org.example.ui.utilities.ScreenshotService.takeScreenshot;
import static org.example.ui.utilities.TestSummary.*;




public class Main {


    public static void main(String[] args) throws Exception {

        DirectoryCleaner.cleanFolders();

        WebDriverManager.chromedriver().setup();

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, String>> users = mapper.readValue(
                new File("users.json"),
                new TypeReference<>() {
                }
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
            String country = user.get("country");


            System.out.println("\n=== Test Running for User: " + username + " ===");
            ChromeOptions options = new ChromeOptions();
            //Reading from applicatiom.properties
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
                        case "DOWNLOAD_UPLOAD":
                            FileManager.downloadExcel(driver, screenName, stringParams);
                            FileManager.uploadFile(driver, templatePath);
                            break;

                        case "DOWNLOAD_UPDATE_UPLOAD":
                            String updatedFile = ExcelGen.generateExcel(templatePath, rules);
                            FileManager.downloadExcel(driver, screenName, stringParams);
                            FileManager.uploadFile(driver, updatedFile);
                            break;
                        case "DOWNLOAD_ONLY":
                            FileManager.downloadExcel(driver, screenName, stringParams);
                            break;
                        case "UPLOAD_ONLY":
                            FileManager.uploadFile(driver, templatePath);
                            break;
                        default:
                            System.out.println("❌ Unknown mode: " + mode);
                    }

                    //  driver.findElement(By.id("menurollin")).click();
                    Event.robustClick(driver, By.id("menurollin"));
                } else {
                    System.out.println("⏩ Skipping screen: " + screenName + " (execute=" + execute + ")");
                }
            }

            driver.quit();

            // writing summary
            writeTestSummary(country);
            resetTestCounter();
            }

            //for resetting old values

        }
    }
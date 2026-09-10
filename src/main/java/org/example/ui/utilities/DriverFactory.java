package org.example.ui.utilities;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.FileInputStream;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Properties;

public class DriverFactory {

    public static Properties loadProperties() throws Exception {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream("application.properties")) {
            properties.load(fis);
        }
        return properties;
    }
    public static WebDriver getDriver() throws Exception {
        Properties properties = loadProperties();
        String downloadDir = getDownloadDirectory(properties);
        return createDriver(properties, downloadDir);
    }
    public static String getDownloadDirectory(Properties properties) {
        String relativeDownloadDir = properties.getProperty("downloadedExcels", "DownloadedExcels");
        return Paths.get(System.getProperty("user.dir"), relativeDownloadDir).toAbsolutePath().toString();
    }

    public static WebDriver createDriver(Properties properties, String downloadDir) {
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        HashMap<String, Object> chromePrefs = new HashMap<>();
        chromePrefs.put("download.default_directory", downloadDir);
        chromePrefs.put("profile.default_content_setting_values.automatic_downloads", 1);
        options.setExperimentalOption("prefs", chromePrefs);

        boolean isHeadless = Boolean.parseBoolean(properties.getProperty("selenium.headless", "false"));
        if (isHeadless) {
            options.addArguments("--headless=new", "--window-size=1920,1080", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
        }

        String zoom = properties.getProperty("zoom", "100");
        options.addArguments("--force-device-scale-factor=0." + zoom);

        WebDriver driver = new ChromeDriver(options);

        if (isHeadless) {
            driver.manage().window().setSize(new Dimension(1920, 1080));
        } else {
            driver.manage().window().maximize();
        }

        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
        return driver;
    }
}
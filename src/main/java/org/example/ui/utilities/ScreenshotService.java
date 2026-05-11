package org.example.ui.utilities;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Base64;
import java.nio.file.Files;

public class ScreenshotService {
    private static final Logger logger = LoggerUtil.getLogger(ScreenshotService.class);

    public static void takeScreenshot(WebDriver driver, String ScreenName) {
        File screenshotsDir = new File("screenshots");
        if (!screenshotsDir.exists()) {
            screenshotsDir.mkdirs();
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS").format(new Date());
        String fileName = "Screenshot_"+ ScreenName +"_"+ timestamp + ".png";

        try {
            // Take screenshot
            File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            FileUtils.copyFile(screenshotFile, new File(screenshotsDir, fileName));
            logger.info("📸 Screenshot captured: {}" , fileName);
        } catch (Exception e) {
            logger.error("❌ Failed to take screenshot: {}", e.getMessage());
        }
    }
    public static String getBase64Screenshot(WebDriver driver) {
        try {
            // Capture screenshot as bytes
            byte[] imageBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            // Encode to Base64 string
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            logger.error("❌ Failed to encode screenshot: {}", e.getMessage());
            return "";
        }
    }
}

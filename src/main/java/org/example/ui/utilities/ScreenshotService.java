package org.example.ui.utilities;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ScreenshotService {
    public static void takeScreenshot(WebDriver driver) {
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

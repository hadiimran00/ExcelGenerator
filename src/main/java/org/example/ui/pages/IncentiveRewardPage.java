package org.example.ui.pages;

import org.example.ui.utilities.Event;
import org.example.ui.utilities.ToastHandles;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;

/**
 * Incentive Reward Upload and Incentive Reward Approval: both screens share the same
 * dropdown ids and Download / Upload Excel buttons.
 */
public class IncentiveRewardPage extends basePage {

    public static final String REWARD_SCREEN_NAME = "Incentive Reward Upload";
    public static final String REWARD_SCREEN_ID = "INCENTIVE_REWARD";
    public static final String APPROVAL_SCREEN_NAME = "Incentive Reward Approval";
    public static final String APPROVAL_SCREEN_ID = "INCENTIVE_REWARD_APPROVAL";
    public static final String TARGET_SCREEN_NAME = "Incentive Target Upload";
    public static final String TARGET_SCREEN_ID = "INCENTIVE_TARGET_UPLOAD";
    public static final String GENERATION_SCREEN_NAME = "Incentive Reward Generation";
    public static final String GENERATION_SCREEN_ID = "INCENTIVE_REWARD_GENERATION";

    // The app's ids: "incentiveCode" holds the incentive type, "IncentiveType" the incentive itself
    private final By incentiveTypeDropdown = By.id("incentiveCode");
    private final By incentiveDropdown = By.id("IncentiveType");
    // Target Upload / Reward Generation ("Tpye" is the app's own spelling)
    private final By programTypeDropdown = By.id("incentiveTpyeCode");
    private final By productGroupDropdown = By.id("ProductGroup");
    private final By entityGroupDropdown = By.id("EntityGroup");
    private final By periodDropdown = By.id("Period");
    private final By distributorDropdown = By.id("Distributor");
    private final By downloadExcelBtn = By.xpath("//button[normalize-space()='Download Excel']");
    private final By uploadExcelBtn = By.xpath("//button[normalize-space()='Upload Excel']");
    private final By fileInput = By.cssSelector("input[type='file']");
    private final By anyToast = By.xpath("//*[contains(@class,'dx-toast-message')]");

    public IncentiveRewardPage(WebDriver driver) {
        super(driver);
    }

    public void selectIncentive(String incentiveType, String incentiveDescription) {
        selectDropdown(incentiveTypeDropdown, incentiveType);
        selectDropdown(incentiveDropdown, incentiveDescription);
    }

    /** Incentive Target Upload / Incentive Reward Generation: the program is picked down to its groups and period. */
    public void selectProgram(String incentiveType, String incentiveDescription, String productGroup, String entityGroup,
                              String period, String distributor) {
        selectDropdown(programTypeDropdown, incentiveType);
        selectDropdown(incentiveDropdown, incentiveDescription);
        selectDropdown(productGroupDropdown, productGroup);
        selectDropdown(entityGroupDropdown, entityGroup);
        selectDropdown(periodDropdown, period);
        selectDropdown(distributorDropdown, distributor);
    }

    /** Returns the Excel downloaded after the click, or null if none arrives. */
    public File downloadExcel(String downloadDir) throws InterruptedException {
        long clickedAt = System.currentTimeMillis();
        Event.robustClick(driver, downloadExcelBtn);
        waitForLoader();
        return waitForDownload(downloadDir, clickedAt, Duration.ofSeconds(30), false);
    }

    /** Uploads the file; returns the message shown afterwards ("" if none appeared). */
    public String uploadExcel(String filePath) {
        waitForLoader();
        // The download's "File downloaded successfully" toast must be gone, or it would be read as the upload result
        new WebDriverWait(driver, Duration.ofSeconds(15))
                .until(ExpectedConditions.invisibilityOfElementLocated(anyToast));
        // The file input may only be rendered once Upload Excel has been clicked
        if (driver.findElements(fileInput).isEmpty()) {
            Event.robustClick(driver, uploadExcelBtn);
        }
        driver.findElement(fileInput).sendKeys(filePath);
        String toast = ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
        waitForLoader();
        return toast;
    }

    /** The error Excel the app downloads after a rejected upload, or null if none arrived. */
    public File waitForErrorFile(String downloadDir, long afterMillis) throws InterruptedException {
        return waitForDownload(downloadDir, afterMillis, Duration.ofSeconds(10), true);
    }

    // A rejected upload downloads "<name>_Error.xlsx"; a download must not pick that file up, and vice versa
    private File waitForDownload(String downloadDir, long afterMillis, Duration timeout, boolean errorFile)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            File[] files = new File(downloadDir).listFiles((dir, name) ->
                    name.endsWith(".xlsx") && name.contains("_Error") == errorFile);
            if (files != null) {
                for (File file : files) {
                    if (file.lastModified() >= afterMillis && file.length() > 0) {
                        return file;
                    }
                }
            }
            Thread.sleep(500);
        }
        return null;
    }
}

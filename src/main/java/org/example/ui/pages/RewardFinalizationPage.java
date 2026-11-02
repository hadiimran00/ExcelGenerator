package org.example.ui.pages;

import org.example.ui.utilities.ToastHandles;
import org.example.ui.utilities.ValidationResult;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.time.Duration;

/**
 * Manual Reward Finalization: finalizing an incentive generates its credit notes.
 */
public class RewardFinalizationPage extends basePage {

    public static final String SCREEN_NAME = "Incentive Manual Reward Finalization";
    public static final String SCREEN_ID = "MANUAL_REWARD_FINALIZATION";

    // "Tpye" is the app's own spelling of the id
    private final By incentiveTypeDropdown = By.id("incentiveTpyeCode");
    private final By incentiveDropdown = By.id("IncentiveType");
    private final By finalizeBtn = By.xpath("//dx-button[@title='Finalize']");

    public RewardFinalizationPage(WebDriver driver) {
        super(driver);
    }

    public void finalizeIncentive(String incentiveType, String incentiveDescription, ValidationResult result) {
        selectDropdown(incentiveTypeDropdown, incentiveType);
        selectDropdown(incentiveDropdown, incentiveDescription);
        click(finalizeBtn);
        String toast = ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(60), result);
        waitForLoader();
        result.pass("Incentive " + incentiveDescription + " finalized: " + toast);
    }
}

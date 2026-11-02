package org.example.ui.pages;

import org.example.ui.utilities.ToastHandles;
import org.example.ui.utilities.ValidationResult;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.time.Duration;

/**
 * Incentive Claim Process: processing an incentive generates its credit note claims.
 */
public class IncentiveClaimProcessPage extends basePage {

    public static final String SCREEN_NAME = "Incentive Claim Process";
    public static final String SCREEN_ID = "INCENTIVE_CLAIM_PROCESS";

    private final By incentiveTypeDropdown = By.id("incentiveType");
    private final By incentiveDropdown = By.id("incentiveId");
    private final By processBtn = By.xpath(
            "//dx-button[@title='Process'] | //*[self::button or self::dx-button][normalize-space()='Process']");

    public IncentiveClaimProcessPage(WebDriver driver) {
        super(driver);
    }

    public void process(String incentiveType, String incentiveDescription, ValidationResult result) {
        selectDropdown(incentiveTypeDropdown, incentiveType);
        selectDropdown(incentiveDropdown, incentiveDescription);
        click(processBtn);
        String toast = ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(60), result);
        waitForLoader();
        result.pass("Incentive " + incentiveDescription + " processed for claim: " + toast);
    }
}

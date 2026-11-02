package org.example.ui.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Claim Inquiry Doc Wise: finds a claim by its project code (dates left at today).
 */
public class ClaimInquiryDocWisePage extends basePage {

    public static final String SCREEN_NAME = "Claim Inquiry Doc Wise";
    public static final String SCREEN_ID = "CLAIM_INQUIRY_DOC_WISE";

    private static final Duration ROW_WAIT = Duration.ofSeconds(10);
    private static final String REWARD_CLAIM_LAYOUT = "Reward Credit Note Claim";

    private final By layoutDropdown = By.id("layoutCode");
    private final By gridFilterCheckbox = By.id("gridFilterCheckbox");
    private final By projectCodeFilter = By.id("rowfilter_TXT__tclm_project_code");
    private final By firstRowClaimAmount = By.id("row_1_claim_amount");
    private final By claimAmountCells = By.cssSelector("td[id^='row_'][id$='_claim_amount']");
    private final By referenceInfoBtn = By.xpath("//dx-button[@aria-label='Reference Info']");
    private final By firstRowIncentiveAmount = By.id("row_1_incentive_amount");

    public ClaimInquiryDocWisePage(WebDriver driver) {
        super(driver);
    }

    /**
     * Opens the screen once, picks the Reward Credit Note Claim layout and turns the filter row on, then filters
     * by project code until its row appears.
     * Returns the claim amount text of the project code's row, or null if none appears within the timeout.
     */
    public String findClaimAmount(String projectCode, Duration timeout) {
        // Row 1 counts only once one of its cells shows our project code, not the previous code's row
        By ourRow = By.xpath("//td[starts-with(@id,'row_1_')][normalize-space()=" + xpathLiteral(projectCode) + "]");
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        navigateToScreen(SCREEN_NAME, SCREEN_ID);
        // The screen remembers the layout; typing it again into the filled field garbles it
        if (!REWARD_CLAIM_LAYOUT.equals(value(layoutDropdown))) {
            selectDropdown(layoutDropdown, REWARD_CLAIM_LAYOUT);
        }
        waitForLoader();
        // The checkbox toggles the filter row and the screen remembers its state, so click it only when the row is hidden
        if (driver.findElements(projectCodeFilter).stream().noneMatch(WebElement::isDisplayed)) {
            click(gridFilterCheckbox);
        }
        do {
            type(projectCodeFilter, projectCode);
            waitForLoader();
            if (waitForPresence(ourRow)) {
                click(firstRowClaimAmount);
                // The grid scrolls sideways, so off-screen cells have empty getText()
                return driver.findElement(firstRowClaimAmount).getAttribute("textContent").trim();
            }
        } while (System.currentTimeMillis() < deadline);
        return null;
    }

    /** Claim rows the grid shows after the project code search (a code's claim should be listed once). */
    public int listedClaimRows() {
        return driver.findElements(claimAmountCells).size();
    }

    /** Opens the first claim's Reference Info and returns its incentive amount (should equal the claim amount). */
    public String referenceInfoIncentiveAmount() {
        click(firstRowClaimAmount);
        click(referenceInfoBtn);
        waitForLoader();
        return wait.until(ExpectedConditions.presenceOfElementLocated(firstRowIncentiveAmount))
                .getAttribute("textContent").trim();
    }

    private boolean waitForPresence(By locator) {
        try {
            return new WebDriverWait(driver, ROW_WAIT).until(d -> !d.findElements(locator).isEmpty());
        } catch (TimeoutException e) {
            return false;
        }
    }

    // Project codes are generated from [A-Z0-9], so a plain quoted literal is always safe
    private static String xpathLiteral(String value) {
        return "'" + value + "'";
    }
}

package org.example.ui.pages;

import org.example.ui.utilities.ToastHandles;
import org.example.ui.utilities.ValidationResult;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Incentive Setup: create an incentive header, fill its claim distribution grid and activate it.
 */
public class IncentiveSetupPage extends basePage {

    public static final String SCREEN_NAME = "Incentive Setup";
    public static final String SCREEN_ID = "INCENTIVE_SETUP";

    private static final Duration TOAST_TIMEOUT = Duration.ofSeconds(30);
    // Everything after the "Incentive Wise Claim Distributor" caption belongs to the claim grid
    // The principal whose project code the claims are generated under
    private static final String UBL_PRINCIPAL = "UBL";
    private static final String CLAIM_GRID ="//*[normalize-space()='Incentive Wise Claim Distributor']/following::*";

    // Header form
    private final By addBtn = By.id("add");
    private final By longDescInput = By.id("longDesc");
    private final By shortDescInput = By.id("shortDesc");
    private final By incentiveTypeDropdown = By.id("incentiveType");
    private final By fromDateInput = By.id("fromDate");
    private final By toDateInput = By.id("toDate");
    // The app gives Maximum Processing Date the same id as Date From; it is the second one on the form
    private final By maxProcessingDateInput = By.xpath("(//input[@id='fromDate'])[2]");
    private final By saveBtn = By.id("save");
    private final By incentiveCodeField = By.id("incentiveCode");
    private final By incentiveCodeTextBox = By.xpath("//dx-text-box[.//input[@id='incentiveCode']]");
    private final By statusDropdown = By.id("status");
    private final By updateBtn = By.id("update");

    // Incentive lister
    private final By gridFilterCheckbox = By.id("gridFilterCheckbox");
    private final By incentiveCodeFilter = By.id("rowfilter_incentiveCode");
    private final By firstRowIncentiveCode = By.id("row_1_incentive_code");

    // Incentive Wise Claim Distributor grid
    private final By selectAllPrincipalsCheckbox = By.id("checkbox-1");
    private final By saveClaimBtn = By.id("saveClaim");

    public IncentiveSetupPage(WebDriver driver) {
        super(driver);
    }

    /** Saves a new incentive with both descriptions set to {@code description}; returns its Incentive Code. */
    public String createIncentive(String description, String incentiveType, LocalDate fromDate, LocalDate toDate,
                                  ValidationResult result) {
        return createIncentive(description, incentiveType, fromDate, toDate, null, result);
    }

    /** As above; a non-null maxProcessingDate also sets Maximum Processing Date. */
    public String createIncentive(String description, String incentiveType, LocalDate fromDate, LocalDate toDate,
                                  LocalDate maxProcessingDate, ValidationResult result) {
        click(addBtn);
        waitForLoader();
        type(longDescInput, description);
        type(shortDescInput, description);
        selectDropdown(incentiveTypeDropdown, incentiveType);
        type(fromDateInput, fromDate.toString());
        type(toDateInput, toDate.toString());
        if (maxProcessingDate != null) {
            type(maxProcessingDateInput, maxProcessingDate.toString());
        }
        click(saveBtn);
        String toast = ToastHandles.validateSuccessMessage(driver, TOAST_TIMEOUT, result);
        waitForLoader();

        String code = waitForIncentiveCode();
        result.pass("Incentive [" + code + "] " + description + " saved: " + toast);
        return code;
    }

    /** The incentive in the lister's first row, before any filter: the newest incentive should be on top. */
    public String topListedIncentiveCode() {
        waitForLoader();
        return wait.until(ExpectedConditions.presenceOfElementLocated(firstRowIncentiveCode))
                .getAttribute("textContent").trim();
    }

    /**
     * Ticks all principals and gives each its project code and claim percentage.
     * Returns the project code given to the UBL principal: CLP / CLM claims are generated under that code only.
     */
    public String saveClaimDistribution(String incentiveCode, List<String> projectCodes, String claimPercentage,
                                        ValidationResult result) {
        selectIncentiveRow(incentiveCode);
        click(selectAllPrincipalsCheckbox);
        String ublProjectCode = null;
        List<String> principals = new ArrayList<>();
        String principalCol = driver.findElement(By.xpath(CLAIM_GRID
                + "//td[@role='columnheader'][contains(normalize-space(),'Principal')]")).getAttribute("aria-colindex");
        for (int row = 0; row < projectCodes.size(); row++) {
            String principal = driver.findElement(By.xpath("(" + CLAIM_GRID + "//tr[contains(@class,'dx-data-row')])["
                    + (row + 1) + "]/td[@aria-colindex='" + principalCol + "']")).getAttribute("textContent").trim();
            principals.add(principal);
            if (principal.toUpperCase().contains(UBL_PRINCIPAL)) {
                ublProjectCode = projectCodes.get(row);
            }
            editClaimCell(row, "Project Code", By.id("projectCode_" + row), projectCodes.get(row));
            // CLP's claim grid has no Claim Percentage column
            if (claimPercentage != null) {
                editClaimCell(row, "Claim Percentage", By.id("claimPercentage_" + row), claimPercentage);
            }
        }
        if (ublProjectCode == null) {
            throw new IllegalStateException("No " + UBL_PRINCIPAL + " principal in the claim distribution: " + principals);
        }
        click(saveClaimBtn);
        String toast = ToastHandles.validateSuccessMessage(driver, TOAST_TIMEOUT, result);
        waitForLoader();
        result.pass("Claim distribution saved (" + (claimPercentage == null ? "" : claimPercentage + "% each, ")
                + "project codes " + projectCodes + ", " + UBL_PRINCIPAL + " = " + ublProjectCode + "): " + toast);
        return ublProjectCode;
    }

    public void activate(String incentiveCode, ValidationResult result) {
        selectDropdown(statusDropdown, "Active");
        click(updateBtn);
        // Activating a CLP program stays on "Updating..." well past 30 s
        String toast = ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(180), result);
        waitForLoader();
        result.pass("Incentive [" + incentiveCode + "] activated: " + toast);
    }

    // The claim distribution grid only loads for the incentive selected in the lister
    private void selectIncentiveRow(String incentiveCode) {
        // The first "Show filter row" checkbox on the page belongs to the lister
        click(gridFilterCheckbox);
        type(incentiveCodeFilter, incentiveCode);
        waitForLoader();
        // Row 1 shows the unfiltered list's first incentive until the filter applies
        wait.until(ExpectedConditions.textToBe(firstRowIncentiveCode, incentiveCode));
        click(firstRowIncentiveCode);
        waitForLoader();
        wait.until(ExpectedConditions.visibilityOfElementLocated(selectAllPrincipalsCheckbox));
    }

    // Claim grid cells are click-to-edit: the input (e.g. projectCode_0) exists only after its cell is clicked
    private void editClaimCell(int row, String columnHeader, By input, String value) {
        String colIndex = driver.findElement(claimGridHeader(columnHeader)).getAttribute("aria-colindex");
        click(By.xpath("(" + CLAIM_GRID + "//tr[contains(@class,'dx-data-row')])[" + (row + 1) + "]/td[@aria-colindex='"
                + colIndex + "']"));
        wait.until(ExpectedConditions.visibilityOfElementLocated(input));
        type(input, value);
    }

    private By claimGridHeader(String columnHeader) {
        return By.xpath(CLAIM_GRID + "//td[@role='columnheader'][normalize-space()='" + columnHeader + "']");
    }

    // The code is assigned by the server, so the field fills in shortly after the save
    private String waitForIncentiveCode() {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> {
                // The readonly input holds the code in "value"; its dx-text-box wrapper repeats it in "title"
                String value = driver.findElement(incentiveCodeField).getAttribute("value");
                if (value == null || value.isBlank()) {
                    value = driver.findElement(incentiveCodeTextBox).getAttribute("title");
                }
                return value == null || value.isBlank() ? null : value.trim();
            });
        } catch (Exception e) {
            throw new IllegalStateException("Incentive Code was not shown after saving the incentive", e);
        }
    }
}

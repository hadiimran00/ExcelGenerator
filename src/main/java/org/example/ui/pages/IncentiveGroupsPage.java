package org.example.ui.pages;

import org.example.ui.utilities.ToastHandles;
import org.example.ui.utilities.ValidationResult;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Incentive Setup's Product Group, Entity Group and Incentive Slab tabs (CLP).
 * Each tab is a header form plus a row-mode grid: "Add a row", fill the new row's cells, save the row, save the tab.
 */
public class IncentiveGroupsPage extends basePage {

    public static final int PRODUCT_GROUP_TAB = 2;
    public static final int ENTITY_GROUP_TAB = 3;
    public static final int SLAB_TAB = 4;
    /** Rule value that ticks the tag box's "Select All" instead of naming values. */
    public static final String SELECT_ALL = "Select All";

    private final By productGroupTypeInput = By.id("productGroupType");
    private final By slabEntityGroupDropdown = By.id("entityGroup");
    private final By slabProductGroupDropdown = By.id("productGroup");
    private final By slabPeriodGroupDropdown = By.id("periodGroup");
    private final By addRowBtn = By.xpath("//div[@title='Add a row']");
    private final By newRowSaveLink = By.xpath("//tr[contains(@class,'dx-row-inserted')]//a[normalize-space()='Save']");
    private final By tabSaveBtn = By.xpath("//dx-button[@title='Save']");
    private final By saveAllBtn = By.xpath("//dx-button[@title='Save All']");

    public IncentiveGroupsPage(WebDriver driver) {
        super(driver);
    }

    public void openTab(int tab) {
        click(By.xpath("//div[@id='tab_" + tab + "']//dx-button"));
        waitForLoader();
    }

    /** Saves a Sales product group; each rule is {field, operator, value}. Returns the save toast. */
    public String saveProductGroup(String description, List<String[]> rules, ValidationResult result) {
        openTab(PRODUCT_GROUP_TAB);
        type(visibleInputAfterLabel("Long Description"), description);
        type(visibleInputAfterLabel("Short Description"), description);
        // Product Group Type defaults to Sales; selecting it again finds no other option
        if (!"Sales".equals(value(productGroupTypeInput))) {
            selectDropdown(productGroupTypeInput, "Sales");
        }
        for (String[] rule : rules) {
            addRow();
            selectDropdown(newRowCell(1), rule[0]);
            selectOperator(newRowCell(2), rule[1]);
            if (SELECT_ALL.equals(rule[2])) {
                selectAllTagBoxValues(newRowCell(3));
            } else {
                // A rule can name several values ("A|B|C"): each is ticked the same way, one by one
                for (String value : rule[2].split("\\|")) {
                    selectDropdown(newRowCell(3), value.trim());
                }
            }
            click(newRowSaveLink);
            waitForLoader();
        }
        click(visible(tabSaveBtn));
        String toast = ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoader();
        return toast;
    }

    /** Saves an entity group of one distributor (AND Distributor = code). Returns the save toast. */
    public String saveEntityGroup(String description, String distributor, ValidationResult result) {
        openTab(ENTITY_GROUP_TAB);
        type(visibleInputAfterLabel("Long Description"), description);
        type(visibleInputAfterLabel("Short Description"), description);
        addRow();
        selectDropdown(newRowCell(1), "AND");
        selectDropdown(newRowCell(2), "Distributor");
        selectOperator(newRowCell(3), "=");
        selectTagBoxValue(newRowCell(4), distributor);
        click(newRowSaveLink);
        waitForLoader();
        click(visible(tabSaveBtn));
        String toast = ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoader();
        return toast;
    }

    /** Saves the slabs for the groups and period; each slab is {longDesc, shortDesc, rangeFrom, rangeTo, rewardPercentage}. */
    public String saveSlabs(String entityGroup, String productGroup, String periodGroup, List<String[]> slabs,
                            ValidationResult result) {
        openTab(SLAB_TAB);
        selectDropdown(slabEntityGroupDropdown, entityGroup);
        selectDropdown(slabProductGroupDropdown, productGroup);
        selectDropdown(slabPeriodGroupDropdown, periodGroup);
        for (String[] slab : slabs) {
            addRow();
            for (int col = 0; col < slab.length; col++) {
                // CLM's repeatOn column is a Yes / No select box, the other columns are typed
                if (driver.findElements(newRowSelectBox(col + 1)).isEmpty()) {
                    type(newRowCell(col + 1), slab[col]);
                } else {
                    // Typing into this two-item list leaves it closed; open it and click the exact item instead
                    selectOperator(newRowCell(col + 1), slab[col]);
                }
            }
            click(newRowSaveLink);
            waitForLoader();
        }
        click(visible(saveAllBtn));
        String toast = ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoader();
        return toast;
    }

    /**
     * Leaves the Slab tab and opens it again, then lists the saved slabs that are not shown with all their values
     * (e.g. "Slab02 [20000.01, 80000, Yes, 4]"). Empty when every slab is shown.
     */
    public List<String> slabsNotShownAfterReopen(String entityGroup, String productGroup, String periodGroup,
                                                 List<String[]> slabs) {
        openTab(PRODUCT_GROUP_TAB);
        openTab(SLAB_TAB);
        if (visibleSlabRows().isEmpty()) {
            selectDropdown(slabEntityGroupDropdown, entityGroup);
            selectDropdown(slabProductGroupDropdown, productGroup);
            selectDropdown(slabPeriodGroupDropdown, periodGroup);
        }
        List<String> rows = visibleSlabRows();
        List<String> missing = new ArrayList<>();
        for (String[] slab : slabs) {
            // A row's text runs its cells together ("Slab01Slab0110020000No2.5"), so look for each value in it
            boolean shown = rows.stream().anyMatch(row -> Arrays.stream(slab).allMatch(row::contains));
            if (!shown) {
                missing.add(slab[0] + " " + Arrays.toString(Arrays.copyOfRange(slab, 2, slab.length)));
            }
        }
        return missing;
    }

    private List<String> visibleSlabRows() {
        List<String> rows = new ArrayList<>();
        for (WebElement row : driver.findElements(By.xpath("//tr[contains(@class,'dx-data-row')]"))) {
            if (row.isDisplayed()) {
                rows.add(row.getAttribute("textContent").replaceAll("\\s+", ""));
            }
        }
        return rows;
    }

    // Operators share characters ("!=" contains "="), so pick the option whose text is exactly the operator
    private void selectOperator(By editor, String operator) {
        click(editor);
        click(By.xpath("(//*[contains(@class,'dx-overlay-content') or @id='dropdown-content']"
                + "//*[contains(@class,'dx-item-content') or contains(@class,'dx-list-item-content')]"
                + "[normalize-space(.)='" + operator + "'])[1]"));
        waitForLoader();
    }

    // Long tag-box lists only render the visible part, so type the value to filter, then tick the matching item
    private void selectTagBoxValue(By editor, String value) {
        click(editor);
        driver.findElement(editor).sendKeys(value);
        waitForLoader();
        click(By.xpath("(//*[contains(@class,'dx-overlay-content')]//*[contains(@class,'dx-list-item-content')]"
                + "[contains(normalize-space(.),'" + value + "')])[1]"));
        driver.findElement(editor).sendKeys(Keys.ESCAPE);
        waitForLoader();
    }

    // Opens the tag box and ticks every option one by one (nothing is typed, so the whole list shows)
    private void selectAllTagBoxValues(By editor) {
        String optionsXpath = "//*[contains(@class,'dx-overlay-content') and not(contains(@class,'dx-state-invisible'))]"
                + "//*[@role='option']";
        click(editor);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(optionsXpath)));
        int count = driver.findElements(By.xpath(optionsXpath)).size();
        // The list re-renders after each tick and a quick next click can be lost, so go over it again until all are ticked.
        // Ticks go on the option's checkbox; options sharing a code ("3 ~ ..." in two languages) are one value
        for (int pass = 1; pass <= 3 && !untickedCodes(optionsXpath).isEmpty(); pass++) {
            for (int i = 1; i <= count; i++) {
                By option = By.xpath("(" + optionsXpath + ")[" + i + "]");
                if (untickedCodes(optionsXpath).contains(optionCode(driver.findElement(option)))) {
                    click(By.xpath("(" + optionsXpath + ")[" + i + "]//div[contains(@class,'dx-list-select-checkbox')]"));
                    waitForLoader();
                }
            }
        }
        Set<String> unticked = untickedCodes(optionsXpath);
        if (!unticked.isEmpty()) {
            throw new IllegalStateException("Select all: options still not ticked: " + unticked);
        }
        driver.findElement(editor).sendKeys(Keys.ESCAPE);
        waitForLoader();
    }

    // Codes ("7" in "7 ~ BEAUTY & WELLBEING") of which no option is ticked yet
    private Set<String> untickedCodes(String optionsXpath) {
        Set<String> all = new LinkedHashSet<>();
        Set<String> ticked = new HashSet<>();
        for (WebElement option : driver.findElements(By.xpath(optionsXpath))) {
            all.add(optionCode(option));
            if ("true".equals(option.getAttribute("aria-selected"))) {
                ticked.add(optionCode(option));
            }
        }
        all.removeAll(ticked);
        return all;
    }

    private static String optionCode(WebElement option) {
        return option.getAttribute("textContent").split("~")[0].trim();
    }

    private void addRow() {
        click(visible(addRowBtn));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//tr[contains(@class,'dx-row-inserted')]")));
    }

    // Editor of the inserted row's Nth column (a dropdown or a text box)
    private By newRowCell(int colIndex) {
        return By.xpath("//tr[contains(@class,'dx-row-inserted')]/td[@aria-colindex='" + colIndex + "']//input[not(@type='hidden')]");
    }

    private By newRowSelectBox(int colIndex) {
        return By.xpath("//tr[contains(@class,'dx-row-inserted')]/td[@aria-colindex='" + colIndex + "']"
                + "//input[@role='combobox']");
    }

    // Hidden tabs keep their fields in the page, so take the label's input that is on screen
    private By visibleInputAfterLabel(String label) {
        // text()[...] matches the label element itself, not a wrapper that also holds the input
        return visible(By.xpath("//*[text()[normalize-space()='" + label + "'] and not(ancestor::td)]/following::input[1]"));
    }

    // The first displayed match, as an exact locator for type()/click()
    private By visible(By locator) {
        List<WebElement> matches = driver.findElements(locator);
        for (int i = 0; i < matches.size(); i++) {
            if (matches.get(i).isDisplayed()) {
                return By.xpath("(" + xpathOf(locator) + ")[" + (i + 1) + "]");
            }
        }
        throw new IllegalStateException("No visible element for " + locator);
    }

    private static String xpathOf(By locator) {
        return locator.toString().replaceFirst("^By\\.xpath: ", "");
    }
}

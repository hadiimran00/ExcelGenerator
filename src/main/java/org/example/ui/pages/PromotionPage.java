package org.example.ui.pages;

import org.apache.logging.log4j.Logger;
import org.example.ui.utilities.Event;
import org.example.ui.utilities.LoggerUtil;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.example.ui.utilities.LoaderWait.waitForLoaderToDisappear;

/**
 * Current Promotion (DT_PROMOTION): the Scheme grid lists each promotion with its allocated and
 * achieved (utilized) value, so it doubles as the budget check for a promo.
 */
public class PromotionPage extends basePage {

    private static final Logger logger = LoggerUtil.getLogger(PromotionPage.class);

    private final By schemeButton = By.xpath("//button[normalize-space()='Scheme']");
    private final By gridFilterCheckbox = By.id("gridFilterCheckbox");
    private final By codeFilter = By.id("rowfilter_code");
    private final By firstRowCode = By.id("row_1_code");
    private final By firstRowAchievedValue = By.id("row_1_achieved_value");

    public PromotionPage(WebDriver driver) {
        super(driver);
    }

    /**
     * Opens Current Promotion, loads the Scheme grid and shows its filter row. Call once per visit;
     * call filterPromo(...) repeatedly afterward to look up different promos on the same visit.
     */
    public void openSchemeGrid() {
        navigateToScreen("Current Promotion", "DT_PROMOTION");
        waitForLoaderToDisappear(driver);
        Event.robustClick(driver, schemeButton);
        waitForLoaderToDisappear(driver);
        Event.robustClick(driver, gridFilterCheckbox);
    }

    public void filterPromo(String promoId) {
        logger.info("📌 Current Promotion: filtering by promo {}", promoId);
        WebElement filter = wait.until(ExpectedConditions.elementToBeClickable(codeFilter));
        filter.clear();
        filter.sendKeys(promoId);
        waitForLoaderToDisappear(driver);
        // The grid re-renders after the filter; wait for row 1 to be the promo so a stale
        // pre-filter row is never read.
        wait.until(ExpectedConditions.textToBe(firstRowCode, promoId));
    }

    /** Achieved (utilized) value of the first row; empty text when nothing has been consumed yet. */
    public String getFirstRowAchievedValue() {
        WebElement cell = wait.until(ExpectedConditions.presenceOfElementLocated(firstRowAchievedValue));
        return cell.getText().trim();
    }
}

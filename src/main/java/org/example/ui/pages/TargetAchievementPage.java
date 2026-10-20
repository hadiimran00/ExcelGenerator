package org.example.ui.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class TargetAchievementPage extends basePage {

    // Locators
    private final By distributorDropdown = By.id("DDL__entity_type_code_02PARENT");
    private final By pjpDropdown = By.id("DDL__ENTITY_TYPE_CODE_03PARENT");
    private final By targetIdDropdown = By.id("DDL__TTGC_TARGET_IDPARENT");
    private final By firstRowAchievementValue = By.id("row_1_achievement_value");
    private final By achievementValueCells = By.cssSelector("td[id^='row_'][id$='_achievement_value']");

    public TargetAchievementPage(WebDriver driver) {
        super(driver);
    }

    public void open() {
        navigateToScreen("Target", "DYL_AS1026");
        waitForLoader();
    }

    public void searchAchievement(String distributor, String pjp, String targetId) {
        selectDropdown(distributorDropdown, distributor);
        selectDropdown(targetIdDropdown, targetId);
        selectDropdown(pjpDropdown, pjp);
        waitForLoader();
    }

    // One value per row (e.g. per product subdivision); no rows yet (nothing achieved) gives an empty list
    public List<String> getAchievementValues() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(d -> !d.findElements(firstRowAchievementValue).isEmpty());
        } catch (TimeoutException e) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (WebElement cell : driver.findElements(achievementValueCells)) {
            values.add(cell.getText().trim());
        }
        return values;
    }

    // No row yet (nothing achieved) is returned as blank
    public String getFirstRowAchievementValue() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(d -> !d.findElements(firstRowAchievementValue).isEmpty());
        } catch (TimeoutException e) {
            return "";
        }
        return driver.findElement(firstRowAchievementValue).getText().trim();
    }
}

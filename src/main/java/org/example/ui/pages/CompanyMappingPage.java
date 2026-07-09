package org.example.ui.pages;

import org.example.ui.utilities.Event;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class CompanyMappingPage extends basePage {

    public CompanyMappingPage(WebDriver driver) {
        super(driver);
    }

    public void searchAndClickDist(String Dist) {
        Event.robustClick(driver, By.id("checkbox-0"));


        By searchBoxLocator = By.id("rowfilter_TXT__ENTITYCODE");

        WebElement searchBox = wait.until(
                ExpectedConditions.elementToBeClickable(searchBoxLocator));

        searchBox.clear();
        searchBox.sendKeys(Dist);

        waitForLoader();
        By descriptionCell = By.id("row_1_description");
        wait.until(ExpectedConditions.elementToBeClickable(descriptionCell));
        Event.robustClick(driver, descriptionCell);

    }

    public void ClickTabAndSearchSellCat(String SellCat) {
        Event.robustClick(driver, By.id("tab_group_1"));
        Event.robustClick(driver, By.id("checkbox-6"));

        By searchBoxLocator = By.id("rowfilter_available.EPL1_BUSENT_LOG_LVL1_CODE");

        WebElement searchBox = wait.until(
                ExpectedConditions.elementToBeClickable(searchBoxLocator));

        searchBox.clear();
        searchBox.sendKeys(SellCat);

        waitForLoader();

    }


}


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

    public void searchDist(String Dist) {

        click(By.id("gridFilterCheckbox"));

        By searchBoxLocator = By.id("rowfilter_TXT__ENTITYCODE");

        WebElement searchBox = wait.until(
                ExpectedConditions.elementToBeClickable(searchBoxLocator));

        searchBox.clear();
        searchBox.sendKeys(Dist);

        waitForLoader();
    }
    public void searchSelLCat(String SellCat) {

        click(By.id("gridFilterCheckbox"));

        By searchBoxLocator = By.id("rowfilter_available.EPL1_BUSENT_LOG_LVL1_CODE");

        WebElement searchBox = wait.until(
                ExpectedConditions.elementToBeClickable(searchBoxLocator));

        searchBox.clear();
        searchBox.sendKeys(SellCat);

        waitForLoader();
    }


    public String getAttributeValue(String fieldId) {
        By locator = By.id(fieldId);
        scrollTo(locator);
        return value(locator);
    }
}

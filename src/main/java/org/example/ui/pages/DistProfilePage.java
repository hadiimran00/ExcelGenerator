package org.example.ui.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class DistProfilePage extends basePage {

  public DistProfilePage(WebDriver driver) {
        super(driver);
    }

    public void searchDist(String Dist) {

        click(By.id("gridFilterCheckbox"));

        By searchBoxLocator = By.id("rowfilter_TXT__EPP1BUSENTPHSLVL1CODE");

        WebElement searchBox = wait.until(
                ExpectedConditions.elementToBeClickable(searchBoxLocator));

        searchBox.clear();
        searchBox.sendKeys(Dist);

        waitForLoader();
    }
    public void openDist(String Dist) {

        // Open the product row
        click(By.xpath("//td[text()='" + Dist + "']"));

        waitForLoader();

    }
    public String getAttributeValue(String fieldId) {
        By locator = By.id(fieldId);
        scrollTo(locator);
        return value(locator);
    }
}

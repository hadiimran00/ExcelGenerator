package org.example.ui.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class ProductPage extends basePage {

    public ProductPage(WebDriver driver) {
        super(driver);
    }

    public void searchProduct(String product) {

        click(By.id("gridFilterCheckbox"));

        By searchBoxLocator = By.id("rowfilter_TXT__pprd_prodcode");

        WebElement searchBox = wait.until(
                ExpectedConditions.elementToBeClickable(searchBoxLocator));

        searchBox.clear();
        searchBox.sendKeys(product);

        waitForLoader();
    }
    public void openProduct(String product) {

        // Open the product row
        click(By.xpath("//td[text()='" + product + "']"));

        waitForLoader();

    }
    public String getAttributeValue(String fieldId) {
        By locator = By.id(fieldId);
        scrollTo(locator);
        return value(locator);
    }
}

package org.example.ui.pages;

import org.apache.commons.math3.stat.descriptive.summary.Product;
import org.example.ui.utilities.ScreenshotService;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class PurchasePricePage extends basePage {

    public PurchasePricePage(WebDriver driver) {
        super(driver);
    }




    public void searchProduct(String product) {

        click(By.id("gridFilterCheckbox"));

        By searchBoxLocator = By.id("rowfilter_code");

        WebElement searchBox = wait.until(
                ExpectedConditions.elementToBeClickable(searchBoxLocator));

        searchBox.clear();
        searchBox.sendKeys(product);

        waitForLoader();
    }
    public void openProductPrice(String product, String tabid) {

        // Open the product row
        click(By.xpath("//td[text()='" + product + "']"));

        waitForLoader();

        // Scroll to the Price Code row
        By priceCodeRow = By.id("row_1_price_code");

        scrollTo(priceCodeRow);

        // Open the price details
        click(priceCodeRow);

        click(By.id(tabid));

        waitForLoader();
    }


    public String getPrice(String screenId) {
        By locator = By.id(screenId);
        scrollTo(locator);
        return value(locator);
    }}
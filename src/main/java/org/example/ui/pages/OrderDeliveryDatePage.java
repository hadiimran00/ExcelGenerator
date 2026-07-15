package org.example.ui.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Handles updating the Delivery Date on an existing Order,
 * before the Locus Excel is generated/uploaded.
 */
public class OrderDeliveryDatePage extends basePage {

    public OrderDeliveryDatePage(WebDriver driver) {
        super(driver);
    }

    /** Searches the order grid using the Order Number column filter. */
    public void searchOrder(String orderNumberColumnId, String orderNumber) {
        click(By.id("gridFilterCheckbox"));

        By searchBoxLocator = By.id(orderNumberColumnId);
        WebElement searchBox = wait.until(
                ExpectedConditions.elementToBeClickable(searchBoxLocator));

        searchBox.clear();
        searchBox.sendKeys(orderNumber);

        waitForLoader();
    }

    /** Opens the order row matching the given Order Number. */
    public void selectOrder(String orderNumber) {
        click(By.xpath("//td[text()='" + orderNumber + "']"));
        waitForLoader();
    }

    /** Sets the delivery date field to today's date and saves. */
    public void updateDeliveryDateToToday() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        By dateField = By.id("DELIVERYDATE");
        scrollTo(dateField);
        type(dateField, today);

        click(By.id("DYP.G......ProcessButton"));
    }
}
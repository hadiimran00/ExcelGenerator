package org.example.ui.pages;

import org.example.ui.utilities.Event;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * Handles updating the Delivery Date on existing Orders,
 * before the Locus Excel is generated/uploaded.
 */
public class OrderDeliveryDatePage extends basePage {

    public OrderDeliveryDatePage(WebDriver driver) {
        super(driver);
    }

    /** Ensure grid filter checkbox is active. */
    public void enableGridFilters() {
        WebElement filterCheckbox = wait.until(
                ExpectedConditions.elementToBeClickable(By.id("gridFilterCheckbox")));
        if (!filterCheckbox.isSelected()) {
            Event.robustClick(driver, By.id("gridFilterCheckbox"));
        }
    }

    /** Searches the order grid using the specified filter column field. */
    public void searchOrder(String orderNumberColumnId, String orderNumber) {
        enableGridFilters();

        By searchBoxLocator = By.id(orderNumberColumnId);
        WebElement searchBox = wait.until(
                ExpectedConditions.elementToBeClickable(searchBoxLocator));

        searchBox.clear();
        searchBox.sendKeys(orderNumber);

        waitForLoader();
    }

    /** Opens/Selects the order row matching the given Order Number. */
    public void selectOrder(String orderNumber) {
        By rowLocator = By.xpath("//td[text()='" + orderNumber + "']");
        click(rowLocator);
        waitForLoader();
    }

    /**
     * Executes the delivery date change workflow for a SINGLE order.
     */
    public void processDeliveryDateForOrder(String pjpNo, String orderNo) {
        // Select PJP Dropdown
        selectDropdown(By.id("DDL__EPJPPJPNODAILY"), pjpNo);

        // Search and select target order
        searchOrder("rowfilter_DOCNO", orderNo);
        selectOrder(orderNo);

        // Process changes and handle alert
        click(By.id("DYP.G......ProcessButton"));
        acceptAlertIfPresent(driver);
    }
}
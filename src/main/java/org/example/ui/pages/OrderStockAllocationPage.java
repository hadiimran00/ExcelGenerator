package org.example.ui.pages;

import org.example.ui.utilities.Event;
import org.example.ui.utilities.ToastHandles;
import org.example.ui.utilities.ValidationResult;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.time.Duration;

import static org.example.ui.utilities.LoaderWait.waitForLoaderToDisappear;

public class OrderStockAllocationPage extends basePage {

    private final By pjpNumField = By.id("pjpNum");
    private final By tab2 = By.id("tab_2");
    private final By gridFilterCheckbox = By.id("gridFilterCheckbox");
    private final By orderNoFilter = By.id("rowfilter_DOCNO");
    private final By rowCheckbox = By.id("checkbox-1");
    private final By notRefreshBtn = By.id("notrefresh");

    public OrderStockAllocationPage(WebDriver driver) {
        super(driver);
    }

    public void unallocateOrder(String pjpNo, String orderNo, ValidationResult result) {
        selectDropdown(pjpNumField, pjpNo);
        waitForLoader();

        click(tab2);
        waitForLoader();

        click(gridFilterCheckbox);
        waitForLoader();

        type(orderNoFilter, orderNo);
        waitForLoader();

        Event.robustClick(driver, rowCheckbox);
        waitForLoader();

        Event.robustClick(driver, notRefreshBtn);
        acceptAlertIfPresent(driver);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoaderToDisappear(driver);
    }
}

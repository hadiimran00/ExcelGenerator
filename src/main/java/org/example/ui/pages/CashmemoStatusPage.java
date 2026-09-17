package org.example.ui.pages;

import org.example.ui.utilities.ToastHandles;
import org.example.ui.utilities.ValidationResult;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.time.Duration;

public class CashmemoStatusPage extends basePage {

    private final By dsrTypeDropdown = By.id("dsrType");
    private final By ginNoDropdown = By.id("ginno");
    private final By gridFilterCheckbox = By.id("gridFilterCheckbox");
    private final By docNoFilterInput = By.id("rowfilter_documentNo");
    private final By row1Checkbox = By.id("row_1_checkbox_1");
    private final By saveAllBtn = By.id("saveallBtn");

    public CashmemoStatusPage(WebDriver driver) {
        super(driver);
    }

    public void deliverOrder(String dsrType, String ginNo, String orderNo, ValidationResult result) {
        selectDropdown(dsrTypeDropdown, dsrType);
        selectDropdown(ginNoDropdown, ginNo);
        click(gridFilterCheckbox);

        type(docNoFilterInput, orderNo);
        waitForLoader();

        click(row1Checkbox);
        click(saveAllBtn);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoader();
    }
}
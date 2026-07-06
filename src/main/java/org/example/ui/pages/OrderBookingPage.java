package org.example.ui.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrderBookingPage extends basePage {

    public OrderBookingPage(WebDriver driver) {
        super(driver);
    }




    public String orderBooking(Map<String, String> testData) {

        String[] parameters = {
                "dsrType",
                "PJPNO",
                "SELLCAT",
                "SECTION",
                "OUTLET"
        };

        // Fill header fields
        for (String parameter : parameters) {
            selectDropdown(By.id(parameter), testData.get(parameter));
        }

        // Open Order Detail
        click(By.id("orderDetail"));

        // Add Row
        click(By.id("addBtn"));

        // Select Product
        selectDropdown(
                By.id("product"),
                testData.get("Product Code")
        );

        // Enter Quantity
        type(
                By.id("quantity3_0"),
                testData.get("QuantityPCS")
        );

        // Save Row
        click(By.id("rowEditBtn_Save_0"));

        // Validate
        click(By.id("validateBtn"));

        // Wait for either success or error
//        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
//
//        wait.until(d ->
//
//                (!d.findElements(By.id("notify_text_success")).isEmpty()
//                        && !d.findElements(By.id("notify_text_success")).get(0).getText().isBlank())
//
//                        ||
//
//                        (!d.findElements(By.id("notify_text_error")).isEmpty()
//                                && !d.findElements(By.id("notify_text_error")).get(0).getText().isBlank())
//        );
//
//        // Success
//        List<WebElement> successList =
//                driver.findElements(By.id("notify_text_success"));
//
//        if (!successList.isEmpty()
//                && !successList.get(0).getText().isBlank()) {
//
//            return successList.get(0).getText().trim();
//        }
//
//        // Error
//        List<WebElement> errorList =
//                driver.findElements(By.id("notify_text_error"));
//
//        if (!errorList.isEmpty()
//                && !errorList.get(0).getText().isBlank()) {
//
//            return errorList.get(0).getText().trim();
//        }
//
//        return "";
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        WebElement error = wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        By.id("notify_text_error"))
        );

        String errorMessage = error.getText().trim();

        System.out.println("Validation Error: " + errorMessage);

        return errorMessage;
    }



    }
package org.example.ui.pages;

import org.example.ui.utilities.Event;
import org.example.ui.utilities.GeneratedDataStore;
import org.example.ui.utilities.ScreenshotService;
import org.example.ui.utilities.ToastHandles;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class OrderBookingPage extends basePage {

    public OrderBookingPage(WebDriver driver) {
        super(driver);
    }

    /**
     * Executes the order booking flow.
     * Uses ToastHandles utility to monitor validate and save notifications.
     * On success, captures the generated order number from element ID "documentNo",
     * updates testData map, and persists values via GeneratedDataStore.
     * On error, returns the error message prefixed with "ERROR: ".
     */
    public String orderBooking(Map<String, String> scenarioData) throws InterruptedException {
        scenarioData.putIfAbsent("dsrType", "Order Booker");

        String[] parameters = {
                "dsrType",
                "PJPNO",
                "SELLCAT",
                "SECTION",
                "OUTLET"
        };

// Fill header fields
        try {
            for (String parameter : parameters) {
                System.out.println("Filling " + parameter + " with " + scenarioData.get(parameter));
                selectDropdown(By.id(parameter), scenarioData.get(parameter));

                waitForLoader();
            }
        } catch (Exception e) {
            for (String parameter : parameters) {
                System.out.println("Filling " + parameter + " with " + scenarioData.get(parameter));
                selectDropdown(By.id(parameter), scenarioData.get(parameter));

                waitForLoader();
            }}

            // Open Order Detail
            Event.robustClick(driver, By.id("orderDetail"));
            waitForLoader();

            // Add Row
            // WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
//        By addBtnLocator = By.id("addBtn");
//        wait.until(
//                ExpectedConditions.elementToBeClickable(addBtnLocator)
//        );
//
//        Event.robustClick(driver, addBtnLocator);
//
//        waitForLoader();

            // Select Product
            selectDropdown(
                    By.id("product"),
                    scenarioData.get("Product Code")
            );

            // Enter Quantity
            type(
                    By.id("quantity3_0"),
                    scenarioData.get("QuantityPCS")
            );
            Thread.sleep(500);
            // Save Row
            try {
                driver.findElement(By.id("rowEditBtn_Save_0")).click();
            } catch (Exception e) {
                Event.robustClick(driver, By.id("rowEditBtn_Save_0"));
                waitForLoader();
            }
            // ==========================================
            // 1. VALIDATE ORDER
            // ==========================================
            try {
                wait.until(ExpectedConditions.elementToBeClickable(By.id("validateBtn")));
                Event.robustClick(driver, By.id("validateBtn"));
                waitForLoader();
            } catch (Exception e) {
                Event.robustClick(driver, By.id("validateBtn"));
                waitForLoader();
            }
            // Wait for notification toast via ToastHandles
            String validateMessage = ToastHandles.waitForNotification(driver, Duration.ofSeconds(10));

            // Check for error notification
            List<WebElement> errorList = driver.findElements(By.id("notify_text_error"));
            if (!errorList.isEmpty() && !errorList.get(0).getText().isBlank()) {
                String errorMessage = errorList.get(0).getText().trim();
                System.err.println("❌ Order Booking Validation Error: " + errorMessage);
                ScreenshotService.takeScreenshot(driver, "Validation_OrderBooking");
                return errorMessage;
            }

            // ==========================================
            // 2. SAVE ORDER
            // ==========================================
            Event.robustClick(driver, By.id("saveBtn"));
            waitForLoader();

            // Wait for notification toast via ToastHandles
            String saveMessage = ToastHandles.waitForNotification(driver, Duration.ofSeconds(10));

            // Check for error notification post-save
            List<WebElement> saveErrorList = driver.findElements(By.id("notify_text_error"));
            if (!saveErrorList.isEmpty() && !saveErrorList.get(0).getText().isBlank()) {
                String errorMessage = saveErrorList.get(0).getText().trim();
                System.err.println("❌ Order Booking Save Error: " + errorMessage);
                ScreenshotService.takeScreenshot(driver, "ERROR_OrderBooking");
                return errorMessage;
            }

            // ==========================================
            // 3. CAPTURE GENERATED ORDER NUMBER & PERSIST
            // ==========================================
            String orderNo = "";
            try {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                WebElement docNoElement = wait.until(
                        ExpectedConditions.visibilityOfElementLocated(By.id("documentNo"))
                );

                // Try value attribute first (input field), fallback to inner text
                orderNo = docNoElement.getAttribute("value");
                if (orderNo == null || orderNo.trim().isEmpty()) {
                    orderNo = docNoElement.getText().trim();
                }

                if (!orderNo.isEmpty()) {
                    // Update in-memory scenario data map
                    scenarioData.put("orderNo", orderNo);
                    scenarioData.put("orderNumber", orderNo);

                    // Store in GeneratedDataStore using testId
                    String testId = scenarioData.get("testId");
                    if (testId != null && !testId.isEmpty()) {
                        GeneratedDataStore.store(testId, "orderNo", orderNo);
                        GeneratedDataStore.store(testId, "orderNumber", orderNo);
                        GeneratedDataStore.store(testId, "documentNo", orderNo);
                    }

                    System.out.println("✅ Generated Order Number captured and stored: " + orderNo);
                }
            } catch (Exception e) {
                System.err.println("⚠️ Could not locate generated documentNo element: " + e.getMessage());
                ScreenshotService.takeScreenshot(driver, "OrdeNO_NotFound");
                waitForLoader();
            }

            return orderNo;
        }
    }
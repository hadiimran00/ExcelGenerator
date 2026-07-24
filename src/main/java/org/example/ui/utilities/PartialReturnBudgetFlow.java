//package org.example.ui.utilities;
//
//import org.apache.logging.log4j.Logger;
//import org.example.ui.pages.loginPage;
//import org.example.ui.utilities.*;
//import org.openqa.selenium.*;
//import org.openqa.selenium.support.ui.ExpectedConditions;
//import org.openqa.selenium.support.ui.WebDriverWait;
//
//import java.time.Duration;
//import java.util.Map;
//
//import static org.example.ui.utilities.LoaderWait.waitForLoaderToDisappear;
//
//public class PartialReturnBudgetFlow {
//    private static final Logger logger = LoggerUtil.getLogger(PartialReturnBudgetFlow.class);
//
//    public static ValidationResult run(
//            WebDriver driver,
//            Map<String, String> scenarioData,
//            String downloadDir
//    ) {
//        ValidationResult result = new ValidationResult("Partial Return Budget Allocation Flow");
//        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
//
//        try {
//            // Extract parameters from scenarioData (with fallbacks to script values)
//            String targetCatalogId = scenarioData.getOrDefault("targetCatalogId", "0952");
//            String dsrName = scenarioData.getOrDefault("dsrName", "leo");
//            String customerName = scenarioData.getOrDefault("customerName", "shah");
//            String fullCustomerCode = scenarioData.getOrDefault("fullCustomerCode", "C0000023667-Shahjalal Super Store");
//            String productCode = scenarioData.getOrDefault("productCode", "68640058");
//            String fullProductName = scenarioData.getOrDefault("fullProductName", "68640058-LIFEBUOY LQ - Automation SKU 4");
//            String orderQty = scenarioData.getOrDefault("orderQty", "37");
//            String returnQty = scenarioData.getOrDefault("returnQty", "15");
//            String tssmUser = scenarioData.getOrDefault("tssmUser", "tssm_bangla");
//            String kpoUser = scenarioData.getOrDefault("kpoUser", "kpo_bangla");
//            String defaultPassword = scenarioData.getOrDefault("password", "Password123!"); // Replace with encrypted/decrypted user pwd
//            String distributorName = scenarioData.getOrDefault("distributor", "15437620-M_S. Atlantic Distribution");
//
//            // ==========================================
//            // STEP 1: INITIAL BUDGET EXPORT
//            // ==========================================
//            logger.info("📌 STEP 1: Exporting Initial Budget Setup for Catalog: {}", targetCatalogId);
//            navigateToScreen(driver, "budget setup");
//
//            Event.robustClick(driver, By.xpath("//span[contains(@class,'dx-checkbox-icon')]"));
//            WebElement catalogFilter = wait.until(ExpectedConditions.visibilityOfElementLocated(
//                    By.xpath("//input[contains(@id,'targetCatalogId') or contains(@class,'rowfilter')]")));
//            catalogFilter.clear();
//            catalogFilter.sendKeys(targetCatalogId);
//
//            Event.robustClick(driver, By.xpath("(//td[contains(@id,'row_1_code')])[1]"));
//            Event.robustClick(driver, By.xpath("//span[text()='Bulk Promo Allocation']"));
//            Event.robustClick(driver, By.id("tab_group_2"));
//            Event.robustClick(driver, By.xpath("//button[contains(text(),'Export to Excel')]"));
//            waitForLoaderToDisappear(driver);
//
//            // ==========================================
//            // STEP 2: ORDER BOOKING
//            // ==========================================
//            logger.info("📌 STEP 2: Creating Order for Customer: {}", fullCustomerCode);
//            navigateToScreen(driver, "order boo");
//
//            Event.robustClick(driver, By.xpath("//span[contains(@id,'clear_button_1')]"));
//
//            selectDropdownOption(driver, wait, By.xpath("//input[contains(@id,'Select')][1]"), dsrName, dsrName);
//            Event.robustClick(driver, By.xpath("//input[contains(@id,'Select')][2]"));
//            Event.robustClick(driver, By.xpath("//div[contains(text(),'All Items Selling Category')]"));
//
//            Event.robustClick(driver, By.xpath("//input[contains(@id,'Select')][3]"));
//            Event.robustClick(driver, By.xpath("//div[contains(text(),'localname1')]"));
//
//            selectDropdownOption(driver, wait, By.xpath("//input[contains(@id,'Select')][4]"), customerName, fullCustomerCode);
//
//            Event.robustClick(driver, By.xpath("//span[text()='Order Detail']"));
//
//            selectDropdownOption(driver, wait, By.xpath("//input[contains(@placeholder,'Type Product') or contains(@id,'Product')]"), productCode, fullProductName);
//
//            WebElement qtyInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//input[contains(@id,'quantity3_0')]")));
//            qtyInput.clear();
//            qtyInput.sendKeys(orderQty);
//
//            Event.robustClick(driver, By.id("rowEditBtn_Save_0"));
//            Event.robustClick(driver, By.xpath("//div[text()='Validation' or contains(@class,'Validation')]"));
//            Event.robustClick(driver, By.xpath("//div[text()='Save' or contains(@class,'Save')]"));
//            waitForLoaderToDisappear(driver);
//
//            // ==========================================
//            // STEP 3: TRANSACTION INQUIRY & MID-BUDGET CHECK
//            // ==========================================
//            logger.info("📌 STEP 3: Verifying Transaction & Mid-Flow Budget Consumption");
//            navigateToScreen(driver, "transa");
//            Event.robustClick(driver, By.xpath("(//td[contains(@id,'row_1_document_no')])[1]"));
//            Event.robustClick(driver, By.xpath("//span[text()='Total Offering']"));
//            Event.robustClick(driver, By.xpath("(//td[contains(@id,'row_1_allocated_quantity')])[1]"));
//
//            navigateToScreen(driver, "budg");
//            Event.robustClick(driver, By.xpath("//span[contains(@class,'dx-checkbox-icon')]"));
//            catalogFilter = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//input[contains(@id,'targetCatalogId') or contains(@class,'rowfilter')]")));
//            catalogFilter.clear();
//            catalogFilter.sendKeys(targetCatalogId.replaceAll("^0+", "")); // Handle leading zero stripping if necessary
//
//            Event.robustClick(driver, By.xpath("(//td[contains(@id,'row_1_code')])[1]"));
//            Event.robustClick(driver, By.xpath("//span[text()='Bulk Promo Allocation']"));
//            Event.robustClick(driver, By.id("tab_group_2"));
//            Event.robustClick(driver, By.xpath("//button[contains(text(),'Export to Excel')]"));
//            waitForLoaderToDisappear(driver);
//
//            // ==========================================
//            // STEP 4: DELIVERY & GOODS ISSUE (GIN)
//            // ==========================================
//            logger.info("📌 STEP 4: Processing Delivery & Goods Issue Note");
//            navigateToScreen(driver, "delivery");
//            Event.robustClick(driver, By.xpath("//span[contains(@id,'clear_button_2')]"));
//            selectDropdownOption(driver, wait, By.xpath("//input[contains(@id,'Select')][1]"), dsrName, dsrName);
//
//            WebElement checkbox = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("(//span[contains(@class,'dx-checkbox-icon')])[1]")));
//            Event.robustClick(driver, checkbox);
//            Event.robustClick(driver, By.xpath("//div[text()='Process']"));
//            waitForLoaderToDisappear(driver);
//
//            navigateToScreen(driver, "good");
//            selectDropdownOption(driver, wait, By.xpath("//input[contains(@id,'Select')][1]"), dsrName, dsrName);
//            selectDropdownOption(driver, wait, By.xpath("//input[contains(@id,'Select')][2]"), customerName, fullCustomerCode);
//
//            Event.robustClick(driver, By.xpath("//span[text()='Cash Memo Selection']"));
//            Event.robustClick(driver, By.xpath("(//span[contains(@class,'dx-checkbox-icon')])[1]"));
//            Event.robustClick(driver, By.xpath("//span[text()='Detail']"));
//            Event.robustClick(driver, By.xpath("//span[text()='Save All']"));
//            Event.robustClick(driver, By.xpath("//div[text()='Header']"));
//
//            // Forward GIN Approval twice
//            Event.robustClick(driver, By.xpath("(//td[contains(@id,'row_1_gin_no')])[1]"));
//            Event.robustClick(driver, By.xpath("//span[text()='Forward']"));
//            driver.findElement(By.xpath("//textarea[contains(@id,'comments')]")).sendKeys("s");
//            Event.robustClick(driver, By.xpath("//div[text()='Save' or contains(@id,'Save')]"));
//
//            Event.robustClick(driver, By.xpath("(//td[contains(@id,'row_1_gin_no')])[1]"));
//            Event.robustClick(driver, By.xpath("//span[text()='Forward']"));
//            driver.findElement(By.xpath("//textarea[contains(@id,'comments')]")).sendKeys("s");
//            Event.robustClick(driver, By.xpath("//span[text()='Save']"));
//
//            // Close popup / Logout KPO
//            try { Event.robustClick(driver, By.cssSelector("i.dx-icon-close")); } catch (Exception ignored) {}
//            logout(driver);
//
//            // ==========================================
//            // STEP 5: TSSM USER APPROVAL
//            // ==========================================
//            logger.info("📌 STEP 5: Re-logging as TSSM User [{}] for Approval", tssmUser);
//            loginPage login = new loginPage(driver);
//            login.login(tssmUser, defaultPassword, null, null);
//
//            selectDropdownOption(driver, wait, By.xpath("//input[contains(@id,'Select')]"), distributorName, distributorName);
//            Event.robustClick(driver, By.id("proceedBtn"));
//            waitForLoaderToDisappear(driver);
//
//            navigateToScreen(driver, "good");
//            Event.robustClick(driver, By.xpath("(//span[contains(@class,'dx-checkbox-icon')])[1]"));
//
//            WebElement docFilter = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//input[contains(@id,'rowfilter_documentNo')]")));
//            docFilter.sendKeys("2607");
//            Event.robustClick(driver, By.cssSelector("span.dx-icon-clear"));
//            docFilter.sendKeys("2");
//
//            logout(driver);
//
//            // ==========================================
//            // STEP 6: KPO FRESH UNLOADING (PARTIAL RETURN)
//            // ==========================================
//            logger.info("📌 STEP 6: Re-logging as KPO [{}] to Process Partial Return", kpoUser);
//            login.login(kpoUser, defaultPassword, null, null);
//
//            navigateToScreen(driver, "fresh");
//            Event.robustClick(driver, By.xpath("//span[contains(@id,'clear_button')]"));
//            selectDropdownOption(driver, wait, By.xpath("//input[contains(@id,'Select')]"), dsrName, dsrName);
//
//            Event.robustClick(driver, By.xpath("(//td[contains(@id,'row_1_document_date')])[1]"));
//            Event.robustClick(driver, By.xpath("(//td[contains(@id,'row_1_document_no')])[1]"));
//            Event.robustClick(driver, By.id("tab_11"));
//            Event.robustClick(driver, By.xpath("//span[text()='Detail']"));
//
//            Event.robustClick(driver, By.id("rowEditBtn_Edit_0"));
//            WebElement returnQtyInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//input[contains(@id,'quantity3_0')]")));
//            returnQtyInput.clear();
//            returnQtyInput.sendKeys(returnQty);
//
//            Event.robustClick(driver, By.xpath("//input[contains(@id,'Select')]"));
//            Event.robustClick(driver, By.xpath("//div[contains(text(),'Wrong Order_No Order')]"));
//
//            Event.robustClick(driver, By.id("rowEditBtn_Save_0"));
//            Event.robustClick(driver, By.xpath("//span[text()='validation' or text()='Validation']"));
//            Event.robustClick(driver, By.xpath("//span[text()='Save']"));
//            waitForLoaderToDisappear(driver);
//
//            // ==========================================
//            // STEP 7: FINAL BUDGET RECONCILIATION
//            // ==========================================
//            logger.info("📌 STEP 7: Exporting Final Restored Budget Setup");
//            navigateToScreen(driver, "budge");
//            Event.robustClick(driver, By.xpath("//span[contains(@class,'dx-checkbox-icon')]"));
//            catalogFilter = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//input[contains(@id,'targetCatalogId') or contains(@class,'rowfilter')]")));
//            catalogFilter.clear();
//            catalogFilter.sendKeys(targetCatalogId.replaceAll("^0+", ""));
//
//            Event.robustClick(driver, By.xpath("(//td[contains(@id,'row_1_code')])[1]"));
//            Event.robustClick(driver, By.xpath("//span[text()='Bulk Promo Allocation']"));
//            Event.robustClick(driver, By.id("tab_group_2"));
//            Event.robustClick(driver, By.xpath("//button[contains(text(),'Export to Excel')]"));
//            waitForLoaderToDisappear(driver);
//
//            result.pass("Partial Return Budget Allocation flow completed successfully. Budget restored for Catalog [" + targetCatalogId + "].");
//
//        } catch (Exception e) {
//            logger.error("❌ Partial Return Budget Allocation flow failed", e);
//            result.fail("Partial Return Budget Allocation flow failed: " + e.getMessage());
//        }
//
//        return result;
//    }
//
//    // --- Helper Methods ---
//
//    private static void navigateToScreen(WebDriver driver, String searchTerm) throws InterruptedException {
//        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
//        waitForLoaderToDisappear(driver);
//
//        try {
//            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("input[placeholder='Search Here']")));
//        } catch (TimeoutException e) {
//            Event.robustClick(driver, By.id("menurollin"));
//            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("input[placeholder='Search Here']")));
//        }
//
//        WebElement search = driver.findElement(By.cssSelector("input[placeholder='Search Here']"));
//        search.clear();
//        search.sendKeys(searchTerm);
//        search.sendKeys(Keys.ENTER);
//        Thread.sleep(500);
//    }
//
//    private static void selectDropdownOption(WebDriver driver, WebDriverWait wait, By inputLocator, String typeText, String optionText) throws InterruptedException {
//        WebElement input = wait.until(ExpectedConditions.elementToBeClickable(inputLocator));
//        input.clear();
//        input.sendKeys(typeText);
//        Thread.sleep(300);
//
//        By optionLocator = By.xpath("//div[contains(@id,'dropdown-content') or contains(@class,'dx-item')]//*[contains(text(),'" + optionText + "')] | //div[contains(text(),'" + optionText + "')]");
//        try {
//            WebElement option = wait.until(ExpectedConditions.elementToBeClickable(optionLocator));
//            option.click();
//        } catch (TimeoutException e) {
//            input.sendKeys(Keys.ENTER);
//        }
//    }
//
//    private static void logout(WebDriver driver) throws InterruptedException {
//        waitForLoaderToDisappear(driver);
//        Event.robustClick(driver, By.xpath("//span[contains(@class,'user-name')]"));
//        Event.robustClick(driver, By.xpath("//li[contains(@id,'logout') or contains(text(),'logout') or contains(text(),'Logout')]"));
//        waitForLoaderToDisappear(driver);
//    }
//}
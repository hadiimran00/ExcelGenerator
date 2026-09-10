package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.example.ui.pages.ProductPage;
import org.example.ui.pages.SANPage;
import org.example.ui.pages.StockInquiryPage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.io.File;
import java.time.Duration;
import java.util.Map;

public class PhysicalStockReconciliationFlow {
    private static final Logger logger = LoggerUtil.getLogger(PhysicalStockReconciliationFlow.class);

    public static ValidationResult run(
            WebDriver driver,
            File downloadedFile,
            String testID,
            Map<String, String> scenarioData,
            String downloadDir) {

        ValidationResult result = new ValidationResult("Physical Stock Reconciliation End-to-End Flow");

        // Load Scenario Configurations
        String adjustmentStr = scenarioData.getOrDefault("Adjustment", "10");
        double adjustment = Double.parseDouble(adjustmentStr);
        String skuColumn = scenarioData.get("SkuColumn");
        String qtyColumn = scenarioData.get("QuantityColumn");
        String targetSku = scenarioData.get("TargetSKU");

        ProductPage productPage = new ProductPage(driver);
        SANPage sanPage = new SANPage(driver);
        StockInquiryPage stockInquiryPage = new StockInquiryPage(driver);

        try {
            // ==========================================
            // PART 1: Incremental Adjustment Cycle (+10)
            // ==========================================

            // Read base quantity from Excel before modification
            double baseQty = ExcelUtils.getCellValueByHeader(downloadedFile, skuColumn, qtyColumn, targetSku);
            logger.info("📊 Base quantity extracted from Excel row for SKU [{}]: {}", targetSku, baseQty);

            // Adjust Excel (+10)
            StockReconciliationExcel.updateQuantity(downloadedFile, skuColumn, qtyColumn, adjustment,targetSku);

            // Upload Modified Excel
            FileManager.uploadFile(driver, "Stock Reconciliation Upload", downloadedFile.getPath());
            String uploadToast = ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));

            if (uploadToast == null) {
                result.fail("Upload Failed: No confirmation toast message received.");
                return result;
            }

            // Route to SAN screen to capture the newly generated Document ID


//           basePage.selectDropdown(driver, "Existing");
            //SANPage sanPage = new SANPage(driver);
            sanPage.selectDropdown(By.id("NewExisting"), "Existing");
            FileManager.downloadExcel(driver, "Stock Reconciliation", null);

            String updatedFilePath = FileManager.getLatestDownloadedFile(downloadDir);
            logger.info("🔍 Verifying that quantity update saved correctly in sheet via file: {}", updatedFilePath);

            File updatedFile = new File(updatedFilePath);
            double uploadedQty = ExcelUtils.getCellValueByHeader(updatedFile, skuColumn, qtyColumn, targetSku);
            double expectedQty = baseQty + adjustment;

            logger.info("📊 Excel Pre-Approval Check -> Found Qty: {}, Expected Qty: {}", uploadedQty, expectedQty);
            if (uploadedQty != expectedQty) {
                result.fail("Excel Validation Failed: Quantity inside the downloaded check sheet does not match the expected adjustment state. Expected: " + expectedQty + ", Found: " + uploadedQty);
                return result;
            }
            logger.info("✅ Excel validation passed! Uploaded quantity verified successfully.");

            // Route to SAN and process multi-stage approval sequence
            logger.info("🔄 Routing to SAN Screen to look up the runtime generated document tracker...");
            productPage.navigateToScreen(scenarioData.get("SANScreen"), scenarioData.get("SANScreenId"));
            sanPage.searchDocument("Physical Stock Reconciliation");
           // sanPage.filterByTodayDate();

            String docNumber = sanPage.getLatestDocumentNumber();
            if (docNumber == null || docNumber.isBlank()) {
                result.fail("Validation Blocked: Unable to extract target Document ID from filtered SAN grid rows.");
                return result;
            }
          sanPage.approveDocument(docNumber);

            // Validate Stock Increase (+10)
            productPage.navigateToScreen(scenarioData.get("StockScreen"), scenarioData.get("StockScreenId"));
            stockInquiryPage.searchSku(targetSku);

            double postIncreaseStock = stockInquiryPage.readPhysicalStock();
            double expectedIncreasedStock = baseQty + adjustment;

            if (postIncreaseStock == expectedIncreasedStock) {
                result.pass("Stock successfully increased from " + baseQty + " to " + postIncreaseStock);
            } else {
                result.fail("Stock increase discrepancy detected. Expected: " + expectedIncreasedStock + ", Found: " + postIncreaseStock);
                return result;
            }

            // ==========================================
            // PART 2: Teardown & Restore Balance Cycle (-10)
            // ==========================================
            logger.info("🔄 Commencing cleanup and teardown cycle to restore baseline parameters...");

            productPage.navigateToScreen(scenarioData.get("ReconciliationScreen"), scenarioData.get("ReconciliationScreenId"));
            sanPage.selectDropdown(By.id("NewExisting"), "Existing");
            searchAndSelectDocumentInReconciliation(driver, docNumber);

        //    clearDownloadDirectory(downloadDir);
            FileManager.downloadExcel(driver, "StockReconTeardownDownload", null);
            File teardownFile = new File(FileManager.getLatestDownloadedFile(downloadDir));

// Subtract 10 to reverse adjustment (This utility accepts the File object)
            StockReconciliationExcel.updateQuantity(teardownFile, skuColumn, qtyColumn, -adjustment, targetSku);

// Upload teardown file - Fix applied here via .getAbsolutePath()
            FileManager.uploadFile(driver, "StockReconRestoreUpload", teardownFile.getAbsolutePath());
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));

// Verify Restored Sheet Data/   clearDownloadDirectory(downloadDir);
            searchAndSelectDocumentInReconciliation(driver, docNumber);
            FileManager.downloadExcel(driver, "StockReconVerificationDownload", null);

            // Perform final SAN Approval for cleanup tracking
            productPage.navigateToScreen(scenarioData.get("SANScreen"), scenarioData.get("SANScreenId"));
            sanPage.searchDocument("Physical Stock Reconciliation");
            sanPage.filterByTodayDate();
            sanPage.openDocument(docNumber);
            sanPage.approveDocument(docNumber);

            // Validate Stock Restored to Original Base level
            productPage.navigateToScreen(scenarioData.get("StockScreen"), scenarioData.get("StockScreenId"));
            stockInquiryPage.searchSku(targetSku);
            double finalStock = stockInquiryPage.readPhysicalStock();

            if (finalStock == baseQty) {
                result.pass("Teardown verified! Inventory successfully returned to initial balance state: " + finalStock);
            } else {
                result.fail("Teardown error: Inventory failed to resolve back to baseline. Expected: " + baseQty + ", Found: " + finalStock);
            }

        } catch (Exception e) {
            result.fail("An unhandled exception broke the reconciliation business sequence: " + e.getMessage() + "");
            ScreenshotService.takeScreenshot(driver, "reconciliation_flow_fatal");
            logger.error("❌ E2E stock reconciliation execution crashed", e);
        }

        return result;
    }



    private static void searchAndSelectDocumentInReconciliation(WebDriver driver, String docNumber) {
        logger.info("🔍 Selecting Document Code: {}", docNumber);
        WebElement input = driver.findElement(By.id("reconDocSearchInput"));
        input.clear();
        input.sendKeys(docNumber);
        Event.robustClick(driver, By.id("reconSearchBtn"));
    }

}
package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.example.ui.pages.ProductPage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.util.Map;

public class ProdEnrichValidation {
    private static final Logger logger = LoggerUtil.getLogger(ProdEnrichValidation.class);

    public static void validate(WebDriver driver,
                                String testId,
                                Map<String, Object> screen,
                                Map<String, String> ScenarioData,
                                ValidationResult result) {

        String productCode = ScenarioData.get("Product Code");
        String FieldToValidate = ScenarioData.get("FieldToValidate");
        String attributeScreenID = ScenarioData.get("attributeScreenID"); // Field ID on UI
        String expectedValue = GeneratedDataStore.get(testId, FieldToValidate);

        // 1. Input Guard Checks
        if (productCode == null || productCode.isBlank()) {
            result.fail("Validation aborted: Product Code not found in Scenario Data.");
            return;
        }

        if (FieldToValidate == null || FieldToValidate.isBlank()) {
            result.fail("Validation aborted: Field to validate (FieldToValidate) is blank.");
            return;
        }

        if (attributeScreenID == null || attributeScreenID.isBlank()) {
            result.fail("Validation aborted: Attribute Screen ID is blank.");
            return;
        }

        try {
            // 2. Navigation and Value Extraction
            ProductPage page = new ProductPage(driver);
            page.navigateToScreen(
                    PostUploadValidator.str(screen, "menuSearch"),
                    PostUploadValidator.str(screen, "validateScreenId")
            );

            page.searchProduct(productCode);
            page.openProduct(productCode);

            String actualValue = page.getAttributeValue(attributeScreenID);
            if (actualValue == null) {
                actualValue = "";
            }

            // 3. Validation and Update Verification Logic
            if (expectedValue != null && expectedValue.equals(actualValue)) {
                logger.info("🎯 Values Match! Field [{}] is [{}]. Proceeding to verify update flow...", FieldToValidate, expectedValue);

                // Click Update only when record successfully matches
                page.click(By.id("update"));

                // Wait for the confirmation toast using your ToastHandles utility
                String finalMsg = ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));

                if (finalMsg.toLowerCase().contains("successful") || finalMsg.toLowerCase().contains("success")) {
                    result.pass("Record " + productCode + " matched on field '" + FieldToValidate + "' (Value: " + expectedValue + ") and updated successfully: " + finalMsg);
                    logger.info("✅ Update confirmed for product: {}", productCode);
                } else if (!finalMsg.isEmpty()) {
                    result.fail("Record " + productCode + " matched, but update action failed with error toast: " + finalMsg);
                    ScreenshotService.takeScreenshot(driver, "update_failed_" + productCode);
                    logger.error("❌ Update failed for product: {}", finalMsg);
                } else {
                    result.fail("Record " + productCode + " matched, but no success/error toast appeared within 30s of clicking Update.");
                    ScreenshotService.takeScreenshot(driver, "update_timeout_" + productCode);
                    logger.error("❌ Update failed: Notification system timed out.");
                }
            } else {
                // Fail immediately due to mismatch and specify exact expected vs actual values
                String mismatchReason = String.format("Field '%s' value mismatch. Expected=[%s], Actual=[%s]",
                        FieldToValidate, expectedValue, actualValue);

                result.fail(mismatchReason);
                ScreenshotService.takeScreenshot(driver, "mismatch_" + productCode);
                logger.error("❌ Product [{}]: {}", productCode, mismatchReason);
            }

        } catch (Exception e) {
            result.fail("Validation processing failed due to runtime exception: " + e.getMessage());
            ScreenshotService.takeScreenshot(driver, "validation_exception_" + productCode);
            logger.error("❌ Error running ProdEnrichValidation for " + productCode, e);
        }
    }
}
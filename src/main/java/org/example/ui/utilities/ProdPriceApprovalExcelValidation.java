package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.example.ui.pages.PurchasePricePage;
import org.example.ui.pages.basePage;
import org.openqa.selenium.WebDriver;

import java.util.Map;

public class ProdPriceApprovalExcelValidation {

    private static final Logger logger =
            LoggerUtil.getLogger(ProdPriceApprovalExcelValidation.class);

    public static void validate(WebDriver driver,
                                String testId,
                                Map<String, Object> screen, Map<String, String> ScenarioData, ValidationResult result) {

        System.out.println("==================================");
        System.out.println("Scenario Data:");
        ScenarioData.forEach((k, v) -> System.out.println(k + " = " + v));
        System.out.println("==================================");
        // Product
        String productCode =
                ScenarioData.get("Product Code");

        System.out.println("Product Code: " + productCode);

        if (productCode.isBlank()) {
            result.fail(" Product Code not found.");
            return;
        }

        // Read from ScenarioData
            String PriceToValidate =
                    ScenarioData.get("PriceToValidate");// e.g. VAT Price

        String priceScreenID =
                ScenarioData.get("priceScreenID");  //field id on UI

        if (PriceToValidate == null || PriceToValidate.isBlank()) {
            result.fail("ScenarioData missing 'PriceToValidate'.");
            return;
        }
        String priceMasterExcelTestId=ScenarioData.get("priceMasterExcelTestId");
        // Expected value from generated Excel
        String expectedPrice =
                GeneratedDataStore.get(priceMasterExcelTestId, PriceToValidate);

        System.out.println(GeneratedDataStore.getAll("03"));

        if (expectedPrice.isBlank()) {
            result.fail("Generated value not found for '" + PriceToValidate + "'");
            return;
        }

        PurchasePricePage page = new PurchasePricePage(driver);

        page.navigateToScreen(PostUploadValidator.str(screen, "menuSearch"),PostUploadValidator.str(screen, "validateScreenId"));


        page.searchProduct(productCode);
        page.openProductPrice(productCode,"tab_2");

        String actualPrice =
                page.getPrice(priceScreenID);

        if (expectedPrice.equals(actualPrice)) {

            result.pass(
                    PriceToValidate +
                            " matched. Expected=" +
                            expectedPrice +
                            " Actual=" +
                            actualPrice
            );
        } else {

            result.fail(
                    PriceToValidate +
                            " mismatch. Expected=" +
                            expectedPrice +
                            " Actual=" +
                            actualPrice
            );
        }
    }
}
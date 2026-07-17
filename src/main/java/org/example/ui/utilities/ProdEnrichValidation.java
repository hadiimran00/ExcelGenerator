package org.example.ui.utilities;

import org.example.ui.pages.ProductPage;
import org.example.ui.pages.basePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.util.Map;

public class ProdEnrichValidation {

    public static void validate(WebDriver driver,
                                String testId,
                                Map<String, Object> screen, Map<String, String> ScenarioData, ValidationResult result) {
        String productCode =
                ScenarioData.get("Product Code");
        String FieldToValidate =
                ScenarioData.get("FieldToValidate");
        String attributeScreenID =
                ScenarioData.get("attributeScreenID"); //field id on UI
        String expectedValue =
                GeneratedDataStore.get(testId, FieldToValidate);



        if (productCode.isBlank()) {
            result.fail(" Product Code not found.");
            return;
        }

        if (FieldToValidate.isBlank()) {
            result.fail(" Field ID not found.");
        }

        ProductPage page = new ProductPage(driver);


        page.navigateToScreen(PostUploadValidator.str(screen, "menuSearch"),PostUploadValidator.str(screen, "validateScreenId"));
        page.searchProduct(productCode);
        page.openProduct(productCode);
        String actualValue= page.getAttributeValue(attributeScreenID);
        page.click(By.id("update"));


        if(expectedValue.equals(actualValue)){
            result.pass(
                    FieldToValidate +
                            " matched. Expected=" +
                            expectedValue +
                            " Actual=" +
                            actualValue
            );
        }
        else {
            result.fail(
                    FieldToValidate +
                            " mismatch. Expected=" +
                            expectedValue +
                            " Actual=" +
                            expectedValue
            );
        }



    }


}
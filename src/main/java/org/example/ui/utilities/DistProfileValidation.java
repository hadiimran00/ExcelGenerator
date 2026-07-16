package org.example.ui.utilities;

import org.example.ui.pages.DistProfilePage;
import org.example.ui.pages.ProductPage;
import org.example.ui.pages.basePage;
import org.openqa.selenium.WebDriver;

import java.util.Map;

public class DistProfileValidation {

    public static void validate(WebDriver driver,
                                String testId,
                                Map<String, Object> screen, Map<String, String> ScenarioData, ValidationResult result) {
        String distCode =
                ScenarioData.get("DistCode");
        String FieldToValidate =
                ScenarioData.get("FieldToValidate");
        String attributeScreenID =
                ScenarioData.get("attributeScreenID"); //field id on UI
        String expectedValue =
                GeneratedDataStore.get(testId, FieldToValidate);



        if (distCode.isBlank()) {
            result.fail(" Dist Code not found.");
            return;
        }

        if (FieldToValidate.isBlank()) {
            result.fail(" Field Code not found.");
        }

       DistProfilePage page = new DistProfilePage(driver);


        page.navigateToScreen(PostUploadValidator.str(screen, "menuSearch"),PostUploadValidator.str(screen, "validateScreenId"));
        page.searchDist(distCode);
        page.openDist(distCode);
        String actualValue= page.getAttributeValue(attributeScreenID);



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
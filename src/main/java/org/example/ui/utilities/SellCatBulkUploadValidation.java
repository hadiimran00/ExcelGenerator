package org.example.ui.utilities;

import org.example.ui.pages.CompanyMappingPage;
import org.example.ui.pages.basePage;
import org.openqa.selenium.WebDriver;

import java.util.Map;

public class SellCatBulkUploadValidation {

    public static void validate(WebDriver driver,
                                String testId,
                                Map<String, Object> screen,
                                Map<String, String> ScenarioData,
                                ValidationResult result) {
        String SellCatCode = ScenarioData.get("SellCatCode");
        String DistCode = ScenarioData.get("DistCode");
        String DataCleanUpExcel = ScenarioData.get("DataCleanUpExcel");

        if (SellCatCode == null || SellCatCode.isBlank()) {
            result.fail("Selling Category Code not found.");
            return;
        }

        CompanyMappingPage page = new CompanyMappingPage(driver);

        page.navigateToScreen(
                PostUploadValidator.str(screen, "menuSearch"),
                PostUploadValidator.str(screen, "validateScreenId")
        );

        page.searchAndClickDist(DistCode);
        page.ClickTabAndSearchSellCat(SellCatCode);

        boolean checkboxStatus = page.isCheckboxChecked(SellCatCode);

        System.out.println("Checkbox Status returned: " + checkboxStatus);

        if (checkboxStatus) {
            result.pass("Selling Category is mapped with Distributor Successfully.");
        } else {
            result.fail("Selling Category is not mapped with Distributor.");
        }
    }
}
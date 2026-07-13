package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.example.ui.pages.PurchasePricePage;
import org.openqa.selenium.WebDriver;

import java.io.File;
import java.util.Map;


public class LocusDownloadExcelValidation {


    private static final Logger logger =
            LoggerUtil.getLogger(org.example.ui.utilities.ProdPriceApprovalExcelValidation.class);

    public static void validate(WebDriver driver,
                                String testId,
                                Map<String, Object> screen, Map<String, String> ScenarioData,File downloaded, ValidationResult result) throws Exception {

        try {
        String bulkOrderUploadTestID=ScenarioData.get("bulkOrderUploadTestID");
        // Expected value from generated Excel
        System.out.println(bulkOrderUploadTestID);
        System.out.println(GeneratedDataStore.getAll("bulkOrderUploadTestID") + "  DATA  ");
        String cashmemoNo =
                GeneratedDataStore.get(bulkOrderUploadTestID, "cashmemoNo");
        System.out.println(cashmemoNo);

        String valueToFind =
                GeneratedDataStore.get(bulkOrderUploadTestID, "cashmemoNo");

        String column =
                ScenarioData.get("downloadColumn");



        boolean found = ExcelUtils.csvContainsValue(
                downloaded,
                column,
                valueToFind);

        if (found) {
            result.pass("Cashmemo '" + valueToFind + "' found in downloaded Excel.");
        } else {
            result.fail("Cashmemo '" + valueToFind + "' not found in column '" + column + "'.");
        }

    } catch (Exception e) {

        logger.error("Locus Download Excel validation failed.", e);

        result.fail("Error during download validation: " + e.getMessage());
    }
}}





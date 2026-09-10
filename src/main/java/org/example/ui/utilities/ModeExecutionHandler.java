package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.example.ui.pages.basePage.findScreenByTestId;

public class ModeExecutionHandler {

    private static final Logger logger = LoggerUtil.getLogger(ModeExecutionHandler.class);

    /**
     * Executes screen actions according to the specified mode.
     */
    public static void executeMode(
            WebDriver driver,
            String mode,
            String screenName,
            String templatePath,
            String downloadDir,
            String validateMode,
            String testID,
            Map<String, String> stringParams,
            Map<String, Map<String, Object>> validations,
            Map<String, String> scenarioData,
            Map<String, Map<String, Object>> rules,
            List<Map<String, Object>> screens,
            String resourcesFolder
    ) throws Exception {

        String updatedFile;

        switch (mode) {
            case "DOWNLOAD_UPLOAD":
                FileManager.uploadFile(driver, screenName, templatePath);
                FileManager.downloadExcel(driver, screenName, stringParams);
                TestSummary.appendValidation(PostUploadValidator.run(driver, validations.get(testID), testID, templatePath, downloadDir, validateMode, scenarioData));
                testDataCleanup(scenarioData, driver);
                break;

            case "DOWNLOAD_UPDATE_UPLOAD":
                updatedFile = ExcelGen.generateExcel(templatePath, rules, testID);
                FileManager.uploadFile(driver, screenName, updatedFile);
                FileManager.downloadExcel(driver, screenName, stringParams);
                TestSummary.appendValidation(PostUploadValidator.run(driver, validations.get(testID), testID, updatedFile, downloadDir, validateMode, scenarioData));
                testDataCleanup(scenarioData, driver);
                break;

            case "DOWNLOAD_ONLY":
                FileManager.downloadExcel(driver, screenName, stringParams);
                TestSummary.appendValidation(PostUploadValidator.run(driver, validations.get(testID), testID, templatePath, downloadDir, validateMode, scenarioData));
                break;

            case "UPLOAD_ONLY":
                FileManager.uploadFile(driver, screenName, templatePath);
                TestSummary.appendValidation(PostUploadValidator.run(driver, validations.get(testID), testID, templatePath, downloadDir, validateMode, scenarioData));
                testDataCleanup(scenarioData, driver);
                break;

            case "PEP":
                Event.robustClick(driver, By.id("row_1_description"));
                FileManager.downloadExcel(driver, screenName, stringParams);
                FileManager.uploadFile(driver, screenName, templatePath);
                break;

            case "PJP": {
                String dsrTestId = scenarioData.get("DSRTestId");
                String headerTestId = scenarioData.get("PJPHeaderTestId");
                String detailTestId = scenarioData.get("PJPConfigTestId");

                Map<String, Object> dsrScreen = findScreenByTestId(screens, dsrTestId, resourcesFolder);
                Map<String, Object> headerScreen = findScreenByTestId(screens, headerTestId, resourcesFolder);
                Map<String, Object> detailScreen = findScreenByTestId(screens, detailTestId, resourcesFolder);

                ValidationResult pjpResult = PJPExcelUpload.run(
                        driver, dsrScreen, headerScreen, detailScreen, scenarioData, downloadDir
                );

                TestSummary.appendValidation(pjpResult);
                break;
            }

            case "DOWNLOAD_MODIFY_UPLOAD": {
                FileManager.downloadExcel(driver, screenName, stringParams);
                String downloadedFile = FileManager.getLatestDownloadedFile(downloadDir);
                String updatedDownloadedFile = ExcelGen.generateExcel(downloadedFile, rules, testID);

                FileManager.uploadFile(driver, screenName, updatedDownloadedFile);
                TestSummary.appendValidation(PostUploadValidator.run(
                        driver, validations.get(testID), testID, updatedDownloadedFile, downloadDir, validateMode, scenarioData
                ));

                testDataCleanup(scenarioData, driver);
                break;
            }

            case "LOCUS_UP": {
                String bulkOrderUploadTestID = scenarioData.get("bulkOrderUploadTestID");
                String deliverScreenId = scenarioData.get("DeliverScreenId");
                String deliveryScreenName = scenarioData.get("DeliveryScreenName");
                String transScreenId = scenarioData.get("TransscreenId");
                String transScreenName = scenarioData.get("TransscreenName");
                String locusUploadTestId = scenarioData.get("LocusUploadTestId");

                if (bulkOrderUploadTestID == null || bulkOrderUploadTestID.isBlank()) {
                    throw new IllegalArgumentException("❌ 'bulkOrderUploadTestID' is missing in scenarioData for Row " + testID);
                }
                if (deliverScreenId == null || deliverScreenId.isBlank()) {
                    throw new IllegalArgumentException("❌ 'DeliverScreenId' (DYL_201080) is missing in scenarioData for Row " + testID);
                }
                if (transScreenId == null || transScreenId.isBlank()) {
                    throw new IllegalArgumentException("❌ 'TransscreenId' (DYL_BG1016) is missing in scenarioData for Row " + testID);
                }

                Map<String, Object> bulkOrderScreen = findScreenByTestId(screens, bulkOrderUploadTestID.trim(), resourcesFolder);
                String targetLocusTestId = (locusUploadTestId != null && !locusUploadTestId.isBlank()) ? locusUploadTestId.trim() : testID;
                Map<String, Object> locusUploadScreen = findScreenByTestId(screens, targetLocusTestId, resourcesFolder);

                Map<String, Object> deliveryDateChangeScreen = new HashMap<>();
                deliveryDateChangeScreen.put("screenId", deliverScreenId.trim());
                deliveryDateChangeScreen.put("screenName", deliveryScreenName != null ? deliveryScreenName.trim() : "delivery date change");

                Map<String, Object> transactionInquiryScreen = new HashMap<>();
                transactionInquiryScreen.put("screenId", transScreenId.trim());
                transactionInquiryScreen.put("screenName", transScreenName != null ? transScreenName.trim() : "BG - Transaction Inquiry");

                ValidationResult locusResult = LocusUploadFlow.run(
                        driver, bulkOrderScreen, deliveryDateChangeScreen, locusUploadScreen, transactionInquiryScreen, scenarioData, downloadDir
                );

                TestSummary.appendValidation(locusResult);
                break;
            }

            case "STOCK_RECON": {
                ValidationResult result = new ValidationResult("Stock Reconciliation [" + testID + "]");
                try {
                    FileManager.downloadExcel(driver, screenName, stringParams);
                    String downloadedFilePath = FileManager.getLatestDownloadedFile(downloadDir);

                    if (downloadedFilePath == null || downloadedFilePath.isBlank()) {
                        result.fail("Stock Reconciliation download failed for screen [" + screenName + "]: No downloaded file path returned.");
                    } else {
                        File downloadedFile = new File(downloadedFilePath);

                        if (!downloadedFile.exists() || !downloadedFile.isFile() || downloadedFile.length() == 0) {
                            result.fail("Stock Reconciliation download failed for screen [" + screenName + "]: File does not exist or is empty at path [" + downloadedFilePath + "].");
                        } else {
                            result = PhysicalStockReconciliationFlow.run(driver, downloadedFile, testID, scenarioData, downloadDir);
                        }
                    }
                } catch (Exception e) {
                    logger.error("❌ Stock Reconciliation step failed for testID [{}]", testID, e);
                    result.fail("Stock Reconciliation Flow failed due to download exception: " + e.getMessage());
                }

                TestSummary.appendValidation(result);
                break;
            }

            case "PARTIAL_RETURN_BUDGET": {
                ValidationResult budgetResult = PartialReturnBudgetFlow.run(driver, scenarioData, downloadDir);
              TestSummary.appendValidation(budgetResult);
                break;
            }

            default:
                logger.warn("❌ Unknown mode: {}", mode);
        }
    }

    private static void testDataCleanup(Map<String, String> scenarioData, WebDriver driver) throws InterruptedException {
        String testDataExcel = scenarioData.get("testDataExcel");
        if (testDataExcel != null && !testDataExcel.isBlank()) {
            FileManager.uploadFileSilent(driver, FileManager.getResourceFile(testDataExcel));
        }
    }
}
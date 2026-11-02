package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.example.ui.pages.ClaimInquiryDocWisePage;
import org.example.ui.pages.CreditNoteInquiryPage;
import org.example.ui.pages.IncentiveClaimProcessPage;
import org.example.ui.pages.IncentiveRewardPage;
import org.example.ui.pages.IncentiveSetupPage;
import org.example.ui.pages.RewardFinalizationPage;
import org.openqa.selenium.WebDriver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import static org.example.ui.pages.basePage.requireScenarioValue;

/**
 * Force Payout end-to-end flow (BD): incentive setup -> claim distribution -> activate ->
 * reward upload -> reward approval -> finalize -> credit note -> claim process -> claims.
 * Checks: credit note amount = reward amount; each principal's claim = reward x claim % / 100.
 */
public class ForcePayoutFlow {

    private static final Logger logger = LoggerUtil.getLogger(ForcePayoutFlow.class);

    private static final String INCENTIVE_TYPE = "Force Payout";
    private static final String CLAIM_PERCENTAGE = "25";
    private static final int PRINCIPAL_COUNT = 4;
    private static final int PROJECT_CODE_LENGTH = 6;
    private static final String PROJECT_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    static final Duration DOCUMENT_WAIT = Duration.ofSeconds(30);
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");

    // Reward Upload / Approval file headers
    private static final String DISTRIBUTOR_CODE = "Distributor Code*";
    private static final String OUTLET_CODE = "Outlet Code*";
    private static final String INCENTIVE_CODE = "Incentive Code*";
    private static final String REWARD_AMOUNT = "Reward Amount*";
    private static final String REWARD_STATUS = "Reward Status*";
    // The approval file's amount column, unlike the reward upload's, has no "*"
    private static final String APPROVAL_REWARD_AMOUNT = "Reward Amount";
    private static final String APPROVED_STATUS = "A";

    public static ValidationResult run(WebDriver driver, Map<String, String> scenarioData, String downloadDir) {
        ValidationResult result = new ValidationResult(
                "Force Payout - Setup, Reward Upload and Approval, Finalization, Credit Note and Claim"
        );

        try {
            validateInputs(scenarioData);

            // Test Data
            String distributor = scenarioData.get("distributor");
            // "C0000023667-Reserved for automation" -> "C0000023667"
            String outlet = scenarioData.get("OUTLET").split("-")[0].trim();
            BigDecimal rewardAmount = new BigDecimal(scenarioData.get("rewardAmount").trim());
            String description = "AutoFP_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
            List<String> projectCodes = randomProjectCodes();

            IncentiveSetupPage setupPage = new IncentiveSetupPage(driver);
            IncentiveRewardPage rewardPage = new IncentiveRewardPage(driver);

            // STEP 1-3: INCENTIVE SETUP (Main has already opened the screen)
            // TEMPORARY CHECK: Finalization only lists ended programs, so run the incentive over the past two days
            LocalDate today = LocalDate.now();
            String incentiveCode = setupPage.createIncentive(description, INCENTIVE_TYPE, today.minusDays(2),
                    today.minusDays(1), result);
            checkNewestOnTop(setupPage, incentiveCode, driver, result);
            setupPage.saveClaimDistribution(incentiveCode, projectCodes, CLAIM_PERCENTAGE, result);
            setupPage.activate(incentiveCode, result);

            // STEP 4: INCENTIVE REWARD UPLOAD - one reward row for the automation outlet
            rewardPage.navigateToScreen(IncentiveRewardPage.REWARD_SCREEN_NAME, IncentiveRewardPage.REWARD_SCREEN_ID);
            rewardPage.selectIncentive(INCENTIVE_TYPE, description);
            File rewardTemplate = checkDownloaded(rewardPage.downloadExcel(downloadDir), "Incentive Reward", driver, result);
            File rewardFile = new File(downloadDir, "ForcePayoutReward_" + incentiveCode + ".xlsx");
            Map<String, Object> reward = new HashMap<>();
            reward.put(DISTRIBUTOR_CODE, distributor);
            reward.put(OUTLET_CODE, outlet);
            reward.put(INCENTIVE_CODE, incentiveCode);
            reward.put(REWARD_AMOUNT, rewardAmount);
            writeSingleRow(rewardTemplate, rewardFile, reward);
            uploadAndCheck(rewardPage, rewardFile, "Incentive Reward upload (" + outlet + " = " + rewardAmount.toPlainString() + ")",
                    downloadDir, driver, result);

            // STEP 5: INCENTIVE REWARD APPROVAL - mark our reward "A"
            rewardPage.navigateToScreen(IncentiveRewardPage.APPROVAL_SCREEN_NAME, IncentiveRewardPage.APPROVAL_SCREEN_ID);
            rewardPage.selectIncentive(INCENTIVE_TYPE, description);
            File approvalDownload = checkDownloaded(rewardPage.downloadExcel(downloadDir), "Incentive Reward Approval", driver, result);
            File approvalFile = new File(downloadDir, "ForcePayoutApproval_" + incentiveCode + ".xlsx");
            checkApprovalRewardAmount(approvalDownload, incentiveCode, outlet, rewardAmount, driver, result);
            approveRewardRow(approvalDownload, approvalFile, incentiveCode, outlet);
            uploadAndCheck(rewardPage, approvalFile, "Incentive Reward Approval upload (" + outlet + " = " + APPROVED_STATUS + ")",
                    downloadDir, driver, result);

            // STEP 6: FINALIZATION - generates the credit note
            RewardFinalizationPage finalizationPage = new RewardFinalizationPage(driver);
            finalizationPage.navigateToScreen(RewardFinalizationPage.SCREEN_NAME, RewardFinalizationPage.SCREEN_ID);
            finalizationPage.finalizeIncentive(INCENTIVE_TYPE, description, result);

            // STEP 7: CREDIT NOTE - one "Credit Note Incentive Reward" for the reward amount
            CreditNoteInquiryPage creditNotePage = new CreditNoteInquiryPage(driver);
            List<String> creditNote = creditNotePage.findIncentiveCreditNote(incentiveCode, DOCUMENT_WAIT);
            if (creditNote == null) {
                throw new IllegalStateException("No " + CreditNoteInquiryPage.INCENTIVE_REWARD_DOCUMENT_TYPE
                        + " found for incentive " + incentiveCode + " within " + DOCUMENT_WAIT.toSeconds() + " seconds");
            }
            checkMatches("Credit note comment for incentive " + incentiveCode,
                    CreditNoteInquiryPage.incentiveCommentPattern(incentiveCode), creditNote.get(0), driver, result);
            checkAmount("Credit note amount for incentive " + incentiveCode, rewardAmount, parseAmount(creditNote.get(1)),
                    driver, result);

            // STEP 7.5: ORDER - the credit note is paid against it (manual DB step), then it is delivered
            String orga = scenarioData.getOrDefault("orgaCode", "010105");
            String orderPjp = scenarioData.get("PJPNO");
            String ginPjp = scenarioData.get("ginPjp");
            OrderSteps orderSteps = new OrderSteps(driver);
            String orderNo = orderSteps.bookOrder(scenarioData);
            orderSteps.updateDeliveryDate(orderPjp, orderNo, result);
            String creditNoteNo = creditNote.get(2);
            ManualStep.pause("Force Payout: attach credit note " + creditNoteNo + " to order " + orderNo
                            + " and clear the incentive claim process log",
                    ClpFlow.creditNotePaymentScripts(orga, distributor, outlet, orderNo, creditNoteNo), Duration.ofMinutes(60));
            result.pass("Manual step done: credit note " + creditNoteNo + " attached to order " + orderNo);
            String ginNo = orderSteps.createAndApproveGin(ginPjp, orderNo, null, null, result);
            orderSteps.deliver(ginPjp, ginNo, orderNo, scenarioData, result);
            result.pass("Order " + orderNo + " delivered (GIN " + ginNo + ") with credit note " + creditNoteNo);
            // The claim counts orders delivered before today, as in CLP / CLM
            ManualStep.pause("Force Payout: set order " + orderNo + " delivered yesterday before the claim process",
                    List.of(ClmFlow.deliveryBackDateScript(orga, distributor, orderNo)), Duration.ofMinutes(60));
            result.pass("Manual step done: order " + orderNo + " delivered yesterday");

            // STEP 8: INCENTIVE CLAIM PROCESS - generates one claim per principal
            IncentiveClaimProcessPage claimProcessPage = new IncentiveClaimProcessPage(driver);
            claimProcessPage.navigateToScreen(IncentiveClaimProcessPage.SCREEN_NAME, IncentiveClaimProcessPage.SCREEN_ID);
            claimProcessPage.process(INCENTIVE_TYPE, description, result);

            // STEP 8.5: INCENTIVE CLAIM JOB (Process Log Viewer's manual run, through the API - as in CLP)
            result.pass("Incentive claim job run for " + distributor + ": "
                    + ProcessLogJobApiClient.generateIncentiveClaim(driver, distributor));

            // STEP 9: CLAIMS - each principal's claim = reward x claim % / 100
            BigDecimal expectedClaim = rewardAmount.multiply(new BigDecimal(CLAIM_PERCENTAGE))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            for (String projectCode : projectCodes) {
                checkClaim(projectCode, "project code " + projectCode + " (" + CLAIM_PERCENTAGE + "% of "
                        + rewardAmount.toPlainString() + ")", expectedClaim, driver, result);
            }

        } catch (Exception e) {
            logger.error("❌ Force Payout flow failed: {}", e.getMessage(), e);
            result.fail("Flow execution encountered an error: " + e.getMessage(), driver);
        }

        return result;
    }

    /** CLP / CLM Claim Inquiry: the claim is generated under the UBL principal's project code only, for the whole reward. */
    static void checkUblClaim(String ublProjectCode, BigDecimal reward, WebDriver driver, ValidationResult result) {
        checkClaim(ublProjectCode, "UBL project code " + ublProjectCode + " = reward", reward, driver, result);
    }

    /**
     * Claim Inquiry Doc Wise, searched by project code: exactly one claim record (no duplicates), its amount as
     * expected, and its Reference Info shows the same incentive amount.
     */
    static void checkClaim(String projectCode, String label, BigDecimal expected, WebDriver driver, ValidationResult result) {
        ClaimInquiryDocWisePage page = new ClaimInquiryDocWisePage(driver);
        String claimAmount = page.findClaimAmount(projectCode, DOCUMENT_WAIT);
        if (claimAmount == null) {
            result.fail("No claim found for project code " + projectCode, driver);
            return;
        }
        int records = page.listedClaimRows();
        if (records == 1) {
            result.pass("Claim Inquiry shows one claim record for project code " + projectCode);
        } else {
            result.fail("Claim Inquiry shows " + records + " claim records for project code " + projectCode
                    + ", expected one", driver);
        }
        BigDecimal claimed = parseAmount(claimAmount);
        checkAmount("Claim for " + label, expected, claimed, driver, result);
        checkAmount("Reference Info incentive amount for project code " + projectCode + " vs claim amount", claimed,
                parseAmount(page.referenceInfoIncentiveAmount()), driver, result);
    }

    /** The incentive just saved should be the first one in the Incentive Setup list (newest on top). */
    static void checkNewestOnTop(IncentiveSetupPage setupPage, String incentiveCode, WebDriver driver, ValidationResult result) {
        String top = setupPage.topListedIncentiveCode();
        if (incentiveCode.equals(top)) {
            result.pass("New incentive " + incentiveCode + " is on top of the Incentive Setup list");
        } else {
            result.fail("New incentive " + incentiveCode + " is not on top of the Incentive Setup list (top is "
                    + top + ")", driver);
        }
    }

    /** Reward Approval file: our row's Reward Amount is a plain number equal to the reward (not e.g. "0E-8"). */
    static void checkApprovalRewardAmount(File download, String incentiveCode, String outlet, BigDecimal expected,
                                          WebDriver driver, ValidationResult result) throws IOException {
        try (FileInputStream in = new FileInputStream(download);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            int ourRow = IncentiveExcel.findRow(sheet, Map.of(INCENTIVE_CODE, incentiveCode, OUTLET_CODE, outlet));
            int amountColumn = IncentiveExcel.headerColumns(sheet, List.of(APPROVAL_REWARD_AMOUNT)).get(APPROVAL_REWARD_AMOUNT);
            String text = ourRow < 0 ? null : IncentiveExcel.cellText(sheet.getRow(ourRow), amountColumn);
            if (text == null || !text.trim().matches("-?\\d+(\\.\\d+)?")) {
                result.fail("Reward Approval file shows Reward Amount [" + text + "] for outlet " + outlet
                        + ", expected a plain number", driver);
            } else {
                checkAmount("Reward Approval file Reward Amount for outlet " + outlet, expected,
                        new BigDecimal(text.trim()), driver, result);
            }
        }
    }

    private static void validateInputs(Map<String, String> data) {
        for (String key : new String[]{"distributor", "OUTLET", "rewardAmount", "PJPNO", "ginPjp"}) {
            requireScenarioValue(data, key, data.get(key));
        }
    }

    static List<String> randomProjectCodes() {
        List<String> codes = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < PRINCIPAL_COUNT; i++) {
            StringBuilder code = new StringBuilder();
            for (int c = 0; c < PROJECT_CODE_LENGTH; c++) {
                code.append(PROJECT_CODE_CHARS.charAt(random.nextInt(PROJECT_CODE_CHARS.length())));
            }
            codes.add(code.toString());
        }
        return codes;
    }

    // ---------- Excel ----------

    // Writes the values as the only data row under the downloaded file's headers.
    // Codes stay text (leading zeros); numeric values (the reward amount) are written as numbers.
    private static void writeSingleRow(File template, File target, Map<String, Object> values) throws IOException {
        try (FileInputStream in = new FileInputStream(template);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> columns = IncentiveExcel.headerColumns(sheet, values.keySet());
            IncentiveExcel.keepOnlyRow(sheet, -1);
            Row row = sheet.createRow(1);
            values.forEach((header, value) -> IncentiveExcel.setCell(row, columns.get(header), value));
            IncentiveExcel.save(workbook, target);
        }
    }

    // Keeps only our incentive/outlet row and marks it approved; other pending rewards are not ours to approve
    static void approveRewardRow(File download, File target, String incentiveCode, String outlet) throws IOException {
        try (FileInputStream in = new FileInputStream(download);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            int ourRow = IncentiveExcel.findRow(sheet, Map.of(INCENTIVE_CODE, incentiveCode, OUTLET_CODE, outlet));
            if (ourRow < 0) {
                throw new IllegalStateException("Approval file " + download.getName() + " has no reward row for outlet "
                        + outlet + " and incentive " + incentiveCode);
            }
            int statusColumn = IncentiveExcel.headerColumns(sheet, List.of(REWARD_STATUS)).get(REWARD_STATUS);
            IncentiveExcel.setCell(sheet.getRow(ourRow), statusColumn, APPROVED_STATUS);
            IncentiveExcel.keepOnlyRow(sheet, ourRow);
            IncentiveExcel.save(workbook, target);
        }
    }

    // ---------- checks ----------

    static File checkDownloaded(File file, String label, WebDriver driver, ValidationResult result) {
        if (file == null) {
            throw new IllegalStateException(label + " Download Excel did not download a file");
        }
        result.pass(label + " file downloaded: " + file.getName());
        return file;
    }

    // Upload inside the flow's own result; a rejected upload lists the error Excel's row messages and stops the flow
    static void uploadAndCheck(IncentiveRewardPage page, File file, String label, String downloadDir,
                                       WebDriver driver, ValidationResult result) throws InterruptedException {
        long uploadedAt = System.currentTimeMillis();
        String toast = page.uploadExcel(file.getAbsolutePath());
        if (toast.toLowerCase().contains("success")) {
            result.pass(label + ": " + toast);
            return;
        }
        String message = label + " failed: " + (toast.isBlank() ? "no message appeared" : toast);
        File errorFile = page.waitForErrorFile(downloadDir, uploadedAt);
        if (errorFile != null) {
            message += " | Excel row errors: " + ExcelValidator.logExcelErrors(errorFile);
        }
        throw new IllegalStateException(message);
    }

    static void checkMatches(String label, Pattern expected, String actual, WebDriver driver, ValidationResult result) {
        if (actual != null && expected.matcher(actual).find()) {
            result.pass(label + ": " + actual);
        } else {
            result.fail(label + ": expected to match [" + expected.pattern() + "] but was [" + actual + "]", driver);
        }
    }

    static void checkAmount(String label, BigDecimal expected, BigDecimal actual, WebDriver driver, ValidationResult result) {
        String message = label + ": expected " + expected.toPlainString() + ", actual " + actual.toPlainString();
        if (expected.subtract(actual).abs().compareTo(AMOUNT_TOLERANCE) <= 0) {
            result.pass(message, driver);
        } else {
            result.fail(message, driver);
        }
    }

    static BigDecimal parseAmount(String text) {
        String digits = text == null ? "" : text.replaceAll("[^0-9.\\-]", "");
        return digits.isEmpty() || "-".equals(digits) || ".".equals(digits) ? BigDecimal.ZERO : new BigDecimal(digits);
    }
}

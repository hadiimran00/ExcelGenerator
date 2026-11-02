package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.example.ui.pages.ClaimInquiryDocWisePage;
import org.example.ui.pages.CreditNoteInquiryPage;
import org.example.ui.pages.IncentiveClaimProcessPage;
import org.example.ui.pages.IncentiveGroupsPage;
import org.example.ui.pages.IncentiveRewardPage;
import org.example.ui.pages.IncentiveSetupPage;
import org.example.ui.pages.RewardFinalizationPage;
import org.example.ui.pages.TransactionInquiryPage;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.example.ui.pages.basePage.requireScenarioValue;
import static org.example.ui.utilities.LoaderWait.waitForLoaderToDisappear;

/**
 * Customer Loyalty Program end-to-end flow (BD): setup (header, claim distribution, product group, entity group,
 * slabs) -> activate -> outlet target -> order delivered -> incentive achievement job -> reward generation ->
 * approval -> finalize -> credit note -> claim process -> second order paid by the credit note, delivered ->
 * incentive claim job -> claims.
 * Check: reward = min(achieved, max target) x slab % (slab picked by target value); credit note and claims pay it.
 * CLM (Cumulative Promotion) has its own flow, {@link ClmFlow}, which reuses this setup and the DB scripts.
 */
public class ClpFlow {

    private static final Logger logger = LoggerUtil.getLogger(ClpFlow.class);

    private static final String INCENTIVE_TYPE = "Customer Loyality Program";
    static final String PERIOD_GROUP = "P001";
    static final List<String[]> PRODUCT_RULES = List.of(
            new String[]{"Demand Channel", "=", "Back Office"},
            new String[]{"Division", "=", IncentiveGroupsPage.SELECT_ALL});
    // {longDesc, shortDesc, rangeFrom, rangeTo, rewardPercentage}
    static final List<String[]> SLABS = List.of(
            new String[]{"Slab01", "Slab01", "100", "20000", "2.5"},
            new String[]{"Slab02", "Slab02", "20000.01", "40000", "5"},
            new String[]{"Slab03", "Slab03", "40000.01", "900000", "10"});

    // Target Upload file
    private static final String OUTLET = "Outlet";
    private static final String TARGET_VALUE = "Target Value";
    private static final String MAX_TARGET_VALUE = "Max Target Value";
    // Reward Generation file
    private static final String MAX_OUTLET_TARGET = "Max outlet Target";
    private static final String REWARD_PERCENTAGE = "Reward Percentage";
    private static final String ACHIEVE_VALUE = "achieve value";
    private static final String CALCULATED_REWARD = "Calculated reward";

    public static ValidationResult run(WebDriver driver, Map<String, String> scenarioData, String downloadDir) {
        String program = "CLP";
        String incentiveType = INCENTIVE_TYPE;
        ValidationResult result = new ValidationResult(
                program + " - Setup, Target, Order, Achievement, Reward, Credit Note and Claim"
        );

        try {
            validateInputs(scenarioData);

            // Test Data
            String distributor = scenarioData.get("distributor");
            String distributorName = scenarioData.getOrDefault("distributorName", "ATLANTIC DISTRIBUTION");
            String outlet = scenarioData.get("OUTLET").split("-")[0].trim();
            String orderPjp = scenarioData.get("PJPNO");
            String ginPjp = scenarioData.get("ginPjp");
            BigDecimal targetValue = new BigDecimal(scenarioData.get("targetValue").trim());
            BigDecimal maxTargetValue = new BigDecimal(scenarioData.get("maxTargetValue").trim());
            // Open question: program dates vs Finalization - offsets from today, default today-today
            LocalDate today = LocalDate.now();
            LocalDate fromDate = today.plusDays(Long.parseLong(scenarioData.getOrDefault("programFromDays", "0")));
            LocalDate toDate = today.plusDays(Long.parseLong(scenarioData.getOrDefault("programToDays", "0")));
            // TEMPORARY - remove after resuming incentive 0000000204.
            // Resume: the user has backdated an earlier run's order / period and cleared the process log,
            // so continue that incentive from job 7020 instead of building a new one
            String resumeIncentiveCode = scenarioData.get("resumeIncentiveCode");
            boolean resume = resumeIncentiveCode != null && !resumeIncentiveCode.isBlank();
            String description = resume ? scenarioData.get("resumeDescription")
                    : "Auto" + program + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
            String productGroup = description + "_PG";
            String entityGroup = description + "_EG";
            List<String> projectCodes = resume ? List.of(scenarioData.get("resumeProjectCodes").split("\\s*,\\s*"))
                    : ForcePayoutFlow.randomProjectCodes();

            IncentiveSetupPage setupPage = new IncentiveSetupPage(driver);
            IncentiveGroupsPage groupsPage = new IncentiveGroupsPage(driver);
            IncentiveRewardPage rewardPage = new IncentiveRewardPage(driver);

            String incentiveCode;
            String orderNo;
            BigDecimal orderNet;
            String ublProjectCode = scenarioData.get("resumeUblProjectCode");
            if (resume) {
                incentiveCode = resumeIncentiveCode;
                orderNo = scenarioData.get("resumeOrderNo");
                orderNet = new BigDecimal(scenarioData.get("resumeOrderNet").trim());
                result.pass("Resuming incentive [" + incentiveCode + "] " + description + " from job 7020 (order " + orderNo
                        + ", net " + orderNet.toPlainString() + ", project codes " + projectCodes + ")");
            } else {

            // STEP 1-2: HEADER + CLAIM DISTRIBUTION (Main has already opened Incentive Setup)
            incentiveCode = setupPage.createIncentive(description, incentiveType, fromDate, toDate, toDate, result);
            ForcePayoutFlow.checkNewestOnTop(setupPage, incentiveCode, driver, result);
            ublProjectCode = setupPage.saveClaimDistribution(incentiveCode, projectCodes, null, result);

            // STEP 3-5: PRODUCT GROUP, ENTITY GROUP, SLABS
            result.pass("Product group " + productGroup + " (BO, all divisions) saved: "
                    + groupsPage.saveProductGroup(productGroup, PRODUCT_RULES, result));
            result.pass("Entity group " + entityGroup + " (distributor " + distributor + ") saved: "
                    + groupsPage.saveEntityGroup(entityGroup, distributor, result));
            result.pass("Slabs 2.5% / 5% / 10% saved for " + PERIOD_GROUP + ": "
                    + groupsPage.saveSlabs(entityGroup, productGroup, PERIOD_GROUP, SLABS, result));
            checkSlabsShown(groupsPage, entityGroup, productGroup, SLABS, driver, result);

            // STEP 6: ACTIVATE
            groupsPage.openTab(1);
            setupPage.activate(incentiveCode, result);

            // STEP 7: INCENTIVE TARGET UPLOAD - target and max target for the automation outlet
            rewardPage.navigateToScreen(IncentiveRewardPage.TARGET_SCREEN_NAME, IncentiveRewardPage.TARGET_SCREEN_ID);
            rewardPage.selectProgram(incentiveType, description, productGroup, entityGroup, PERIOD_GROUP, distributorName);
            File targetDownload = ForcePayoutFlow.checkDownloaded(rewardPage.downloadExcel(downloadDir), "Incentive Target", driver, result);
            File targetFile = new File(downloadDir, program + "Target_" + incentiveCode + ".xlsx");
            checkTargetFile(targetDownload, null, driver, result);
            setOutletTarget(targetDownload, targetFile, outlet, targetValue, maxTargetValue);
            ForcePayoutFlow.uploadAndCheck(rewardPage, targetFile, "Incentive Target upload (" + outlet + " target "
                    + targetValue.toPlainString() + ", max " + maxTargetValue.toPlainString() + ")", downloadDir, driver, result);

            // STEP 8: ORDER - booked from the back office and delivered
            OrderSteps orderSteps = new OrderSteps(driver);
            orderNo = orderSteps.bookOrder(scenarioData);
            orderSteps.updateDeliveryDate(orderPjp, orderNo, result);
            String ginNo = orderSteps.createAndApproveGin(ginPjp, orderNo, null, null, result);
            orderSteps.deliver(ginPjp, ginNo, orderNo, scenarioData, result);
            orderNet = readNetAmount(driver, scenarioData, orderPjp, orderNo);
            result.pass("Order " + orderNo + " delivered (GIN " + ginNo + "), net amount " + orderNet.toPlainString());
            }

            // STEP 8.5: MANUAL - before the achievement job the order is back-dated and the incentive process log cleared
            // (the tester runs the scripts; the automation never writes to the DB). A resumed run has done it already.
            String orga = scenarioData.getOrDefault("orgaCode", "010105");
            if (!resume) {
                ManualStep.pause(program + ": back-date order " + orderNo + " and clear the incentive process log before the achievement job",
                        List.of(orderBackDateScript(orga, distributor, orderNo), processLogScript(orga, distributor)),
                        Duration.ofMinutes(60));
                result.pass("Manual step done: order " + orderNo + " back-dated, incentive process log cleared for " + distributor);
            }

            // STEP 9: INCENTIVE ACHIEVEMENT JOB (Process Log Viewer's manual run, through the API)
            result.pass("Incentive achievement job run for " + distributor + ": "
                    + ProcessLogJobApiClient.runIncentiveAchievement(driver, distributor));

            // STEP 9.5: MANUAL - after the job, the incentive and its period are back-dated
            if (!resume) {
                ManualStep.pause(program + ": back-date incentive " + incentiveCode + " and its period after the achievement job",
                        backDateScripts(orga, incentiveCode), Duration.ofMinutes(60));
                result.pass("Manual step done: incentive " + incentiveCode + " and its period back-dated");
            }

            // STEP 10: REWARD GENERATION - downloaded once; our outlet must already be achieved
            File generationFile = new File(downloadDir, program + "RewardGeneration_" + incentiveCode + ".xlsx");
            // Opens Reward Generation and selects the program (its Period is listed once the job has achieved it)
            Runnable openRewardGeneration = () -> {
                rewardPage.navigateToScreen(IncentiveRewardPage.GENERATION_SCREEN_NAME, IncentiveRewardPage.GENERATION_SCREEN_ID);
                rewardPage.selectProgram(incentiveType, description, productGroup, entityGroup, PERIOD_GROUP, distributorName);
            };
            Map<String, BigDecimal> reward = downloadAchievedReward(rewardPage, openRewardGeneration, downloadDir, outlet,
                    generationFile, driver, result);
            BigDecimal achieved = reward.get(ACHIEVE_VALUE);
            BigDecimal calculatedReward = reward.get(CALCULATED_REWARD);
            // The file must carry the target we uploaded, or the calculation below would check the app against itself
            ForcePayoutFlow.checkAmount("Reward file Target Value for " + outlet, targetValue, reward.get(TARGET_VALUE), driver, result);
            ForcePayoutFlow.checkAmount("Reward file Max outlet Target for " + outlet, maxTargetValue,
                    reward.get(MAX_OUTLET_TARGET), driver, result);
            checkAtLeast("Achieved value for " + outlet + " vs order " + orderNo + " net", orderNet, achieved, driver, result);
            BigDecimal slabPercent = slabPercent(targetValue);
            ForcePayoutFlow.checkAmount("Reward percentage for target " + targetValue.toPlainString(), slabPercent,
                    reward.get(REWARD_PERCENTAGE), driver, result);
            BigDecimal expectedReward = achieved.min(reward.get(MAX_OUTLET_TARGET)).multiply(slabPercent)
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            ForcePayoutFlow.checkAmount("Calculated reward = min(achieved " + achieved.toPlainString() + ", max "
                    + reward.get(MAX_OUTLET_TARGET).toPlainString() + ") x " + slabPercent.toPlainString() + "%",
                    expectedReward, calculatedReward, driver, result);
            ForcePayoutFlow.uploadAndCheck(rewardPage, generationFile, "Incentive Reward Generation upload", downloadDir, driver, result);

            // STEP 11: REWARD APPROVAL - mark our reward "A"
            rewardPage.navigateToScreen(IncentiveRewardPage.APPROVAL_SCREEN_NAME, IncentiveRewardPage.APPROVAL_SCREEN_ID);
            rewardPage.selectIncentive(incentiveType, description);
            File approvalDownload = ForcePayoutFlow.checkDownloaded(rewardPage.downloadExcel(downloadDir), "Incentive Reward Approval", driver, result);
            File approvalFile = new File(downloadDir, program + "Approval_" + incentiveCode + ".xlsx");
            ForcePayoutFlow.checkApprovalRewardAmount(approvalDownload, incentiveCode, outlet, calculatedReward, driver, result);
            ForcePayoutFlow.approveRewardRow(approvalDownload, approvalFile, incentiveCode, outlet);
            ForcePayoutFlow.uploadAndCheck(rewardPage, approvalFile, "Incentive Reward Approval upload (" + outlet + " = A)",
                    downloadDir, driver, result);

            // STEP 12: FINALIZATION -> CREDIT NOTE for the calculated reward
            RewardFinalizationPage finalizationPage = new RewardFinalizationPage(driver);
            finalizationPage.navigateToScreen(RewardFinalizationPage.SCREEN_NAME, RewardFinalizationPage.SCREEN_ID);
            finalizationPage.finalizeIncentive(incentiveType, description, result);
            List<String> creditNote = new CreditNoteInquiryPage(driver).findIncentiveCreditNote(incentiveCode, ForcePayoutFlow.DOCUMENT_WAIT);
            if (creditNote == null) {
                throw new IllegalStateException("No " + CreditNoteInquiryPage.INCENTIVE_REWARD_DOCUMENT_TYPE
                        + " found for incentive " + incentiveCode);
            }
            ForcePayoutFlow.checkMatches("Credit note comment for incentive " + incentiveCode,
                    CreditNoteInquiryPage.incentiveCommentPattern(incentiveCode), creditNote.get(0), driver, result);
            ForcePayoutFlow.checkAmount("Credit note amount for incentive " + incentiveCode, calculatedReward,
                    ForcePayoutFlow.parseAmount(creditNote.get(1)), driver, result);

            // STEP 13: CLAIM PROCESS -> CLAIMS: the claims under our project codes add up to the reward
            IncentiveClaimProcessPage claimProcessPage = new IncentiveClaimProcessPage(driver);
            claimProcessPage.navigateToScreen(IncentiveClaimProcessPage.SCREEN_NAME, IncentiveClaimProcessPage.SCREEN_ID);
            claimProcessPage.process(incentiveType, description, result);

            // STEP 14: SECOND ORDER - the incentive credit note is paid against it
            OrderSteps claimOrderSteps = new OrderSteps(driver);
            String claimOrderNo = claimOrderSteps.bookOrder(scenarioData);
            claimOrderSteps.updateDeliveryDate(orderPjp, claimOrderNo, result);
            result.pass("Second order " + claimOrderNo + " booked for the credit note");

            // STEP 14.5: MANUAL - attach the credit note to the order, mark it used, clear the claim process log
            String creditNoteNo = creditNote.get(2);
            ManualStep.pause(program + ": attach credit note " + creditNoteNo + " to order " + claimOrderNo
                            + " and clear the incentive claim process log",
                    creditNotePaymentScripts(orga, distributor, outlet, claimOrderNo, creditNoteNo), Duration.ofMinutes(60));
            result.pass("Manual step done: credit note " + creditNoteNo + " attached to order " + claimOrderNo
                    + ", incentive claim process log cleared for " + distributor);

            // STEP 15: DELIVER THE SECOND ORDER, then back-date it
            String claimGinNo = claimOrderSteps.createAndApproveGin(ginPjp, claimOrderNo, null, null, result);
            claimOrderSteps.deliver(ginPjp, claimGinNo, claimOrderNo, scenarioData, result);
            result.pass("Order " + claimOrderNo + " delivered (GIN " + claimGinNo + ")");
            ManualStep.pause(program + ": back-date order " + claimOrderNo + " before the incentive claim job",
                    List.of(orderBackDateScript(orga, distributor, claimOrderNo)), Duration.ofMinutes(60));
            result.pass("Manual step done: order " + claimOrderNo + " back-dated");

            // STEP 16: INCENTIVE CLAIM JOB (Process Log Viewer's manual run, through the API)
            result.pass("Incentive claim job run for " + distributor + ": "
                    + ProcessLogJobApiClient.generateIncentiveClaim(driver, distributor));

            // STEP 17: CLAIM INQUIRY - one claim, under the UBL principal's project code, for the reward
            ForcePayoutFlow.checkUblClaim(ublProjectCode, calculatedReward, driver, result);

        } catch (Exception e) {
            logger.error("❌ {} flow failed: {}", program, e.getMessage(), e);
            result.fail("Flow execution encountered an error: " + e.getMessage(), driver);
        }

        return result;
    }

    static String processLogScript(String orga, String distributor) {
        return "DELETE FROM public.snd_tr_plg_process_log WHERE porg_orgacode='" + orga
                + "' AND plg_field_comb='DIST~INCENTIVE' AND plg_value_comb='" + distributor + "~INCENTIVE';";
    }

    // Before the achievement job: order dated 2 days ago, delivered yesterday
    static String orderBackDateScript(String orga, String distributor, String orderNo) {
        LocalDate orderDate = LocalDate.now().minusDays(2);
        LocalDate deliveryDate = LocalDate.now().minusDays(1);
        return "UPDATE public.snd_tr_cmm_cashmemo_master SET tcmm_docm_date='" + orderDate + "', tcmm_docm_date_time='"
                + orderDate + " 12:00:00', tcmm_delivery_date='" + deliveryDate + "', tcmm_delivery_date_time='"
                + deliveryDate + " 12:00:00', tcmm_actual_delivery_time='" + deliveryDate + " 12:00:00'"
                + " WHERE porg_orgacode='" + orga + "' AND pbet_busentity_type='DIST' AND epp1_busent_phs_lvl1_code='"
                + distributor + "' AND pdot_docmtype='CM-01' AND tcmm_docno='" + orderNo + "';";
    }

    // After the claim process: the credit note pays the second order (payment row, credit note status R),
    // and the incentive claim process log is cleared so the claim job runs again
    static List<String> creditNotePaymentScripts(String orga, String distributor, String outlet,
                                                         String orderNo, String creditNoteNo) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return List.of(
                "INSERT INTO public.snd_tr_cmm_cashmemo_payment (porg_orgacode, pbet_busentity_type, epp1_busent_phs_lvl1_code, "
                        + "pdot_docmtype, tcmm_docno, tcmp_paym_srno, ptrn_tran_nature, ppym_paymmode, pbnk_bank_code, pbnb_bnbr_code, "
                        + "tcmp_bankbr_name, tcmp_otherinfo, tcmp_instrument_type, tcmp_instrument_refno, tcmp_instrument_date, pcur_currcode, "
                        + "tcmp_exg_rate_base, tcmp_exg_rate_rep1, tcmp_exg_rate_rep2, tcmp_amount_fcy, tcmp_amount_bcy, tcmp_amount_rcy1, "
                        + "tcmp_amount_rcy2, created_by, create_date, modified_by, modified_date, tdsl_deposit_slip_srno, tcmp_payment_date, "
                        + "pins_instrument_status, tcmp_instrument_status_date, porg_orgacode_phs_lvl2_f, pbet_busent_type_phs_lvl2_f, "
                        + "epp2_busent_code_phs_lvl2_f, pbet_busentity_type_ref, epp1_busent_phs_lvl1_code_ref, pdot_docmtype_ref, "
                        + "tfdh_docno_ref, tcmp_sync_key, epjp_pjpno_daily, epjp_sr_no_daily, tcmp_cm_allowed, tcmp_status) VALUES ('"
                        + orga + "', 'DIST', '" + distributor + "', 'CM-01', '" + orderNo + "', 1, null, null, null, null, null, null, "
                        + "null, null, null, null, null, null, null, null, null, null, null, 'automation', '" + now + "', 'automation', '"
                        + now + "', null, null, null, null, null, null, null, null, null, 'CRN-05', '" + creditNoteNo
                        + "', null, null, null, null, '1');",
                "UPDATE public.snd_tr_fdh_financial_doc_hdr SET tfdh_status='R' WHERE porg_orgacode='" + orga
                        + "' AND epp1_busent_phs_lvl1_code='" + distributor + "' AND tfdh_docno='" + creditNoteNo
                        + "' AND epp1_busent_code_phs_lvl1_c='" + outlet + "';",
                "DELETE FROM public.snd_tr_plg_process_log WHERE porg_orgacode='" + orga
                        + "' AND plg_field_comb='INCENTIVE~INCENTIVE_CLAIM' AND plg_value_comb LIKE '%" + distributor + "%';");
    }

    // After the achievement job: the incentive, its period group and period run from 2 days ago to yesterday, processed today
    static List<String> backDateScripts(String orga, String incentiveCode) {
        LocalDate from = LocalDate.now().minusDays(2);
        LocalDate to = LocalDate.now().minusDays(1);
        String where = " WHERE porg_orgacode='" + orga + "' AND tinc_incentive='" + incentiveCode + "'";
        return List.of(
                "UPDATE public.tgt_pr_ipd_incentive_period SET tipd_date_from='" + from + "', tipd_date_to='" + to + "'"
                        + where + " AND tipg_period_group='" + PERIOD_GROUP + "';",
                "UPDATE public.tgt_pr_ipg_inc_period_group SET tipg_date_from='" + from + "', tipg_date_to='" + to + "'"
                        + where + " AND tipg_period_group='" + PERIOD_GROUP + "';",
                "UPDATE public.tgt_pr_inc_incentive SET tinc_date_from='" + from + "', tinc_date_to='" + to
                        + "', tinc_process_date='" + LocalDate.now() + "'" + where + ";");
    }

    static void validateInputs(Map<String, String> data) {
        String[] keys = {"distributor", "OUTLET", "dsrType", "PJPNO", "SELLCAT", "SECTION", "productCode", "QuantityPCS",
                "ginPjp", "targetValue", "maxTargetValue"};
        for (String key : keys) {
            requireScenarioValue(data, key, data.get(key));
        }
    }

    // The slab whose range holds the outlet's target value
    static BigDecimal slabPercent(BigDecimal targetValue) {
        for (String[] slab : SLABS) {
            if (targetValue.compareTo(new BigDecimal(slab[2])) >= 0 && targetValue.compareTo(new BigDecimal(slab[3])) <= 0) {
                return new BigDecimal(slab[4]);
            }
        }
        throw new IllegalStateException("Target value " + targetValue.toPlainString() + " is outside every slab");
    }

    static BigDecimal readNetAmount(WebDriver driver, Map<String, String> scenarioData, String pjp, String orderNo) {
        TransactionInquiryPage page = new TransactionInquiryPage(driver);
        page.navigateToScreen("Transaction Inquiry", "DYL_102014");
        waitForLoaderToDisappear(driver);
        page.selectDocumentType("Sales");
        page.selectPjp(pjp);
        String netColumn = scenarioData.getOrDefault("netAmountColumnId", "row_1_net_amount");
        return ForcePayoutFlow.parseAmount(page.getDocumentColumnValue(orderNo, netColumn)).abs();
    }

    // ---------- Excel ----------

    // Keeps only the outlet's row and sets its target / max target
    /**
     * Downloaded target file: every outlet listed once and, when {@code expectedHeaders} is given, exactly those
     * columns in that order.
     */
    static void checkTargetFile(File download, List<String> expectedHeaders, WebDriver driver, ValidationResult result)
            throws IOException {
        try (FileInputStream in = new FileInputStream(download);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (expectedHeaders != null) {
                List<String> headers = new ArrayList<>();
                for (int c = 0; c < sheet.getRow(0).getLastCellNum(); c++) {
                    headers.add(IncentiveExcel.cellText(sheet.getRow(0), c));
                }
                if (headers.equals(expectedHeaders)) {
                    result.pass("Target file has the expected columns " + expectedHeaders);
                } else {
                    result.fail("Target file columns are " + headers + ", expected " + expectedHeaders, driver);
                }
            }
            int outletColumn = IncentiveExcel.headerColumns(sheet, List.of(OUTLET)).get(OUTLET);
            Set<String> seen = new HashSet<>();
            Set<String> repeated = new TreeSet<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                String outlet = row == null ? "" : IncentiveExcel.cellText(row, outletColumn);
                if (!outlet.isEmpty() && !seen.add(outlet)) {
                    repeated.add(outlet);
                }
            }
            if (repeated.isEmpty()) {
                result.pass("Target file lists each of its " + seen.size() + " outlets once");
            } else {
                result.fail("Target file lists these outlets more than once: " + repeated, driver);
            }
        }
    }

    /** After saving, the Slab tab still shows every slab with all its values when it is opened again. */
    static void checkSlabsShown(IncentiveGroupsPage groupsPage, String entityGroup, String productGroup,
                                List<String[]> slabs, WebDriver driver, ValidationResult result) {
        List<String> missing = groupsPage.slabsNotShownAfterReopen(entityGroup, productGroup, PERIOD_GROUP, slabs);
        if (missing.isEmpty()) {
            result.pass("All " + slabs.size() + " slabs are shown when the Slab tab is opened again");
        } else {
            result.fail("Slabs not shown when the Slab tab is opened again: " + missing, driver);
        }
    }

    static void setOutletTarget(File download, File target, String outlet, BigDecimal targetValue,
                                        BigDecimal maxTargetValue) throws IOException {
        try (FileInputStream in = new FileInputStream(download);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            int ourRow = requireOutletRow(sheet, outlet, download);
            Map<String, Integer> columns = IncentiveExcel.headerColumns(sheet, List.of(TARGET_VALUE, MAX_TARGET_VALUE));
            Row row = sheet.getRow(ourRow);
            IncentiveExcel.setCell(row, columns.get(TARGET_VALUE), targetValue);
            IncentiveExcel.setCell(row, columns.get(MAX_TARGET_VALUE), maxTargetValue);
            IncentiveExcel.keepOnlyRow(sheet, ourRow);
            IncentiveExcel.save(workbook, target);
        }
    }

    // Downloads Reward Generation until the outlet's achieve value is above 0; saves only that row for the upload
    // Downloads Reward Generation once; no file (no period offered) or achieve value 0 fails the step
    private static Map<String, BigDecimal> downloadAchievedReward(IncentiveRewardPage page, Runnable openProgram, String downloadDir,
                                                                  String outlet, File target, WebDriver driver, ValidationResult result)
            throws IOException, InterruptedException {
        openProgram.run();
        File download = page.downloadExcel(downloadDir);
        if (download == null) {
            throw new IllegalStateException("Incentive Reward Generation downloaded no file (no period offered)"
                    + " - the achievement job did not achieve the program");
        }
        result.pass("Incentive Reward Generation file downloaded: " + download.getName());
        try (FileInputStream in = new FileInputStream(download);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            int ourRow = requireOutletRow(sheet, outlet, download);
            List<String> headers = List.of(TARGET_VALUE, MAX_OUTLET_TARGET, REWARD_PERCENTAGE, ACHIEVE_VALUE, CALCULATED_REWARD);
            Map<String, Integer> columns = IncentiveExcel.headerColumns(sheet, headers);
            Row row = sheet.getRow(ourRow);
            BigDecimal achieved = IncentiveExcel.cellNumber(row, columns.get(ACHIEVE_VALUE));
            if (achieved.signum() <= 0) {
                throw new IllegalStateException("Achieve value for outlet " + outlet + " is 0 - the achievement job did not count the order");
            }
            Map<String, BigDecimal> values = new HashMap<>();
            for (String header : headers) {
                values.put(header, IncentiveExcel.cellNumber(row, columns.get(header)));
            }
            IncentiveExcel.keepOnlyRow(sheet, ourRow);
            IncentiveExcel.save(workbook, target);
            return values;
        }
    }

    private static int requireOutletRow(Sheet sheet, String outlet, File file) {
        int row = IncentiveExcel.findRow(sheet, Map.of(OUTLET, outlet));
        if (row < 0) {
            throw new IllegalStateException(file.getName() + " has no row for outlet " + outlet);
        }
        return row;
    }

    static void checkAtLeast(String label, BigDecimal minimum, BigDecimal actual, WebDriver driver, ValidationResult result) {
        String message = label + ": " + actual.toPlainString() + " >= " + minimum.toPlainString();
        if (actual.compareTo(minimum) >= 0) {
            result.pass(message);
        } else {
            result.fail(message + " is false", driver);
        }
    }
}

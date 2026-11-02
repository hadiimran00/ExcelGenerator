package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.example.ui.pages.ClaimInquiryDocWisePage;
import org.example.ui.pages.CreditNoteInquiryPage;
// import org.example.ui.pages.IncentiveClaimProcessPage;
import org.example.ui.pages.IncentiveGroupsPage;
import org.example.ui.pages.IncentiveRewardPage;
import org.example.ui.pages.IncentiveSetupPage;
import org.example.ui.pages.executeNodeJob;
import org.example.ui.Main;
import org.openqa.selenium.WebDriver;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cumulative Promotion (CLM) end-to-end flow (BD): setup (header, claim distribution, product group, entity group,
 * slabs) -> activate -> outlet target -> order delivered -> incentive achievement job -> credit note -> claim process
 * -> second order paid by the credit note, delivered -> incentive claim job -> claims.
 * Same setup as CLP ({@link ClpFlow}, whose setup and DB scripts it reuses), but the jobs run on the Node Executor
 * as the node user ("demo"): 197 / 8020 / 8020 gives the achievement, the reward and its credit note (no Reward
 * Generation, Approval and Finalization screens), 197 / 8025 / 8025 the claims.
 * Check: min(order net, max target) x slab % <= credit note <= max target x slab %; the claims pay the credit note.
 */
public class ClmFlow {

    private static final Logger logger = LoggerUtil.getLogger(ClmFlow.class);

    private static final String INCENTIVE_TYPE = "Cumulative Promotion";
    // Node Executor: organization, then group = group source
    private static final String NODE_ORGANIZATION = "197";
    private static final String ACHIEVEMENT_JOB = "8020";
    private static final String CLAIM_JOB = "8025";
    // {longDesc, shortDesc, rangeFrom, rangeTo, repeatOn, rewardPercentage}
    private static final List<String[]> SLABS = List.of(
            new String[]{"Slab01", "Slab01", "100", "20000", "No", "2.5"},
            new String[]{"Slab02", "Slab02", "20000.01", "80000", "Yes", "4"});
    // The CLM Incentive Target download's columns, in order (SDMS-2321)
    private static final List<String> TARGET_FILE_COLUMNS = List.of("Incentive", "Product Group Id", "Product Description",
            "Entity Group", "Entity Group Description", "Period", "Period Description", "Distributor",
            "Geo Hierarchy Name", "Outlet", "Target Value", "Max Target Value", "Achieved Value");

    public static ValidationResult run(WebDriver driver, Map<String, String> scenarioData, String downloadDir) {
        ValidationResult result = new ValidationResult(
                "CLM - Setup, Target, Order, Achievement, Credit Note and Claim"
        );

        try {
            ClpFlow.validateInputs(scenarioData);

            // Test Data
            String distributor = scenarioData.get("distributor");
            String distributorName = scenarioData.getOrDefault("distributorName", "ATLANTIC DISTRIBUTION");
            String outlet = scenarioData.get("OUTLET").split("-")[0].trim();
            String orderPjp = scenarioData.get("PJPNO");
            String ginPjp = scenarioData.get("ginPjp");
            String orga = scenarioData.getOrDefault("orgaCode", "010105");
            BigDecimal targetValue = new BigDecimal(scenarioData.get("targetValue").trim());
            BigDecimal maxTargetValue = new BigDecimal(scenarioData.get("maxTargetValue").trim());
            LocalDate today = LocalDate.now();
            LocalDate fromDate = today.plusDays(Long.parseLong(scenarioData.getOrDefault("programFromDays", "0")));
            LocalDate toDate = today.plusDays(Long.parseLong(scenarioData.getOrDefault("programToDays", "0")));
            String description = "AutoCLM_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
            String productGroup = description + "_PG";
            String entityGroup = description + "_EG";
            List<String> projectCodes = ForcePayoutFlow.randomProjectCodes();

            IncentiveSetupPage setupPage = new IncentiveSetupPage(driver);
            IncentiveGroupsPage groupsPage = new IncentiveGroupsPage(driver);
            IncentiveRewardPage rewardPage = new IncentiveRewardPage(driver);

            // STEP 1-2: HEADER + CLAIM DISTRIBUTION (Main has already opened Incentive Setup)
            String incentiveCode = setupPage.createIncentive(description, INCENTIVE_TYPE, fromDate, toDate, toDate, result);
            ForcePayoutFlow.checkNewestOnTop(setupPage, incentiveCode, driver, result);
            String ublProjectCode = setupPage.saveClaimDistribution(incentiveCode, projectCodes, null, result);

            // STEP 3-5: PRODUCT GROUP, ENTITY GROUP, SLABS
            result.pass("Product group " + productGroup + " (BO, all divisions) saved: "
                    + groupsPage.saveProductGroup(productGroup, ClpFlow.PRODUCT_RULES, result));
            result.pass("Entity group " + entityGroup + " (distributor " + distributor + ") saved: "
                    + groupsPage.saveEntityGroup(entityGroup, distributor, result));
            result.pass("Slabs 2.5% (repeat No) / 4% (repeat Yes) saved for " + ClpFlow.PERIOD_GROUP + ": "
                    + groupsPage.saveSlabs(entityGroup, productGroup, ClpFlow.PERIOD_GROUP, SLABS, result));
            ClpFlow.checkSlabsShown(groupsPage, entityGroup, productGroup, SLABS, driver, result);

            // STEP 6: ACTIVATE
            groupsPage.openTab(1);
            setupPage.activate(incentiveCode, result);

            // STEP 7: INCENTIVE TARGET UPLOAD - target and max target for the automation outlet
            rewardPage.navigateToScreen(IncentiveRewardPage.TARGET_SCREEN_NAME, IncentiveRewardPage.TARGET_SCREEN_ID);
            rewardPage.selectProgram(INCENTIVE_TYPE, description, productGroup, entityGroup, ClpFlow.PERIOD_GROUP, distributorName);
            File targetDownload = ForcePayoutFlow.checkDownloaded(rewardPage.downloadExcel(downloadDir), "Incentive Target", driver, result);
            File targetFile = new File(downloadDir, "ClmTarget_" + incentiveCode + ".xlsx");
            ClpFlow.checkTargetFile(targetDownload, TARGET_FILE_COLUMNS, driver, result);
            ClpFlow.setOutletTarget(targetDownload, targetFile, outlet, targetValue, maxTargetValue);
            ForcePayoutFlow.uploadAndCheck(rewardPage, targetFile, "Incentive Target upload (" + outlet + " target "
                    + targetValue.toPlainString() + ", max " + maxTargetValue.toPlainString() + ")", downloadDir, driver, result);

            // STEP 8: ORDER - booked from the back office and delivered
            OrderSteps orderSteps = new OrderSteps(driver);
            String orderNo = orderSteps.bookOrder(scenarioData);
            orderSteps.updateDeliveryDate(orderPjp, orderNo, result);
            String ginNo = orderSteps.createAndApproveGin(ginPjp, orderNo, null, null, result);
            orderSteps.deliver(ginPjp, ginNo, orderNo, scenarioData, result);
            BigDecimal orderNet = ClpFlow.readNetAmount(driver, scenarioData, orderPjp, orderNo);
            result.pass("Order " + orderNo + " delivered (GIN " + ginNo + "), net amount " + orderNet.toPlainString());

            // STEP 8.5: MANUAL - before the achievement job the order is delivered yesterday and today's achievement
            // log cleared (the tester runs the scripts; the automation never writes to the DB)
            ManualStep.pause("CLM: set order " + orderNo + " delivered yesterday and clear today's achievement log before job "
                            + ACHIEVEMENT_JOB,
                    List.of(deliveryBackDateScript(orga, distributor, orderNo), ClpFlow.processLogScript(orga, distributor)),
                    Duration.ofMinutes(60));
            result.pass("Manual step done: order " + orderNo + " delivered yesterday, achievement log cleared for " + distributor);

            // STEP 9: ACHIEVEMENT JOB on the Node Executor - also makes the reward and its credit note
            runNodeJob(driver, ACHIEVEMENT_JOB, scenarioData, result);
            result.pass("Node Executor job " + NODE_ORGANIZATION + " / " + ACHIEVEMENT_JOB + " / " + ACHIEVEMENT_JOB
                    + " executed as " + Main.currentUser.getNodeUsername());

            // STEP 10: CREDIT NOTE
            List<String> creditNote = new CreditNoteInquiryPage(driver).findIncentiveCreditNote(incentiveCode, ForcePayoutFlow.DOCUMENT_WAIT);
            if (creditNote == null) {
                throw new IllegalStateException("No " + CreditNoteInquiryPage.INCENTIVE_REWARD_DOCUMENT_TYPE
                        + " found for incentive " + incentiveCode + " after job " + ACHIEVEMENT_JOB);
            }
            ForcePayoutFlow.checkMatches("Credit note comment for incentive " + incentiveCode,
                    CreditNoteInquiryPage.incentiveCommentPattern(incentiveCode), creditNote.get(0), driver, result);
            // No Reward Generation file here, so the achieved value is unknown: it is at least the order's net
            // (other orders of the outlet count too), and the reward is capped at the max target
            BigDecimal reward = ForcePayoutFlow.parseAmount(creditNote.get(1));
            BigDecimal slabPercent = slabPercent(targetValue);
            BigDecimal lowest = orderNet.min(maxTargetValue).multiply(slabPercent)
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            BigDecimal highest = maxTargetValue.multiply(slabPercent).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            ClpFlow.checkAtLeast("Credit note amount for incentive " + incentiveCode + " vs min(order " + orderNo + " net "
                    + orderNet.toPlainString() + ", max " + maxTargetValue.toPlainString() + ") x "
                    + slabPercent.toPlainString() + "%", lowest, reward, driver, result);
            ClpFlow.checkAtLeast("Max target " + maxTargetValue.toPlainString() + " x " + slabPercent.toPlainString()
                    + "% vs credit note amount " + reward.toPlainString(), reward, highest, driver, result);

            // STEP 11: CLAIM PROCESS - not a step of the CLM document
            // IncentiveClaimProcessPage claimProcessPage = new IncentiveClaimProcessPage(driver);
            // claimProcessPage.navigateToScreen(IncentiveClaimProcessPage.SCREEN_NAME, IncentiveClaimProcessPage.SCREEN_ID);
            // claimProcessPage.process(INCENTIVE_TYPE, description, result);

            // STEP 12: SECOND ORDER - the incentive credit note is paid against it
            OrderSteps claimOrderSteps = new OrderSteps(driver);
            String claimOrderNo = claimOrderSteps.bookOrder(scenarioData);
            claimOrderSteps.updateDeliveryDate(orderPjp, claimOrderNo, result);
            result.pass("Second order " + claimOrderNo + " booked for the credit note");

            // STEP 12.5: MANUAL - attach the credit note to the order, mark it used, clear the claim process log
            String creditNoteNo = creditNote.get(2);
            ManualStep.pause("CLM: attach credit note " + creditNoteNo + " to order " + claimOrderNo
                            + " and clear the incentive claim process log",
                    ClpFlow.creditNotePaymentScripts(orga, distributor, outlet, claimOrderNo, creditNoteNo), Duration.ofMinutes(60));
            result.pass("Manual step done: credit note " + creditNoteNo + " attached to order " + claimOrderNo
                    + ", incentive claim process log cleared for " + distributor);

            // STEP 13: DELIVER THE SECOND ORDER, then set it delivered yesterday
            String claimGinNo = claimOrderSteps.createAndApproveGin(ginPjp, claimOrderNo, null, null, result);
            claimOrderSteps.deliver(ginPjp, claimGinNo, claimOrderNo, scenarioData, result);
            result.pass("Order " + claimOrderNo + " delivered (GIN " + claimGinNo + ")");
            ManualStep.pause("CLM: set order " + claimOrderNo + " delivered yesterday before job " + CLAIM_JOB,
                    List.of(deliveryBackDateScript(orga, distributor, claimOrderNo)), Duration.ofMinutes(60));
            result.pass("Manual step done: order " + claimOrderNo + " delivered yesterday");

            // STEP 14: CLAIM JOB on the Node Executor
            runNodeJob(driver, CLAIM_JOB, scenarioData, result);
            result.pass("Node Executor job " + NODE_ORGANIZATION + " / " + CLAIM_JOB + " / " + CLAIM_JOB
                    + " executed as " + Main.currentUser.getNodeUsername());

            // STEP 15: CLAIM INQUIRY - one claim, under the UBL principal's project code, for the credit note
            ForcePayoutFlow.checkUblClaim(ublProjectCode, reward, driver, result);

        } catch (Exception e) {
            logger.error("❌ CLM flow failed: {}", e.getMessage(), e);
            result.fail("Flow execution encountered an error: " + e.getMessage(), driver);
        }

        return result;
    }

    // Node Executor in a separate browser logged in as the node user ("demo")
    private static void runNodeJob(WebDriver driver, String job, Map<String, String> scenarioData, ValidationResult result)
            throws Exception {
        Map<String, String> nodeJob = new HashMap<>();
        nodeJob.put("Organization", NODE_ORGANIZATION);
        nodeJob.put("Group", job);
        nodeJob.put("GroupDetails", job);
        // The Execute popup takes the integrator user, as in the Locus flow (executeNodeJob's default)
        if (scenarioData.containsKey("ExecuteUser")) {
            nodeJob.put("ExecuteUser", scenarioData.get("ExecuteUser"));
            nodeJob.put("ExecutePassword", scenarioData.get("ExecutePassword"));
        }
        new executeNodeJob(driver).run(nodeJob, result);
    }

    // The slab is picked by the outlet's target value
    private static BigDecimal slabPercent(BigDecimal targetValue) {
        for (String[] slab : SLABS) {
            if (targetValue.compareTo(new BigDecimal(slab[2])) >= 0 && targetValue.compareTo(new BigDecimal(slab[3])) <= 0) {
                return new BigDecimal(slab[5]);
            }
        }
        throw new IllegalStateException("Target value " + targetValue.toPlainString() + " is outside every slab");
    }

    // Delivery and actual delivery yesterday; the order date stays as booked
    static String deliveryBackDateScript(String orga, String distributor, String orderNo) {
        LocalDate deliveryDate = LocalDate.now().minusDays(1);
        return "UPDATE public.snd_tr_cmm_cashmemo_master SET tcmm_delivery_date='" + deliveryDate
                + "', tcmm_delivery_date_time='" + deliveryDate + " 12:00:00', tcmm_actual_delivery_time='" + deliveryDate
                + " 12:00:00' WHERE porg_orgacode='" + orga + "' AND pbet_busentity_type='DIST' AND epp1_busent_phs_lvl1_code='"
                + distributor + "' AND pdot_docmtype='CM-01' AND tcmm_docno='" + orderNo + "';";
    }
}

package org.example.ui.pages;

import org.example.ui.utilities.ManualStep;
import org.example.ui.utilities.ToastHandles;
import org.example.ui.utilities.ValidationResult;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class CashmemoStatusPage extends basePage {

    public static final String SCREEN_NAME = "Cashmemo Status";
    public static final String SCREEN_ID = "CASHMEMO-STATUS";

    private final By dsrTypeDropdown = By.id("dsrType");
    private final By ginNoDropdown = By.id("ginno");
    private final By gridFilterCheckbox = By.id("gridFilterCheckbox");
    private final By docNoFilterInput = By.id("rowfilter_documentNo");
    private final By row1Checkbox = By.id("row_1_checkbox_1");
    private final By saveAllBtn = By.id("saveallBtn");

    public CashmemoStatusPage(WebDriver driver) {
        super(driver);
    }

    public void deliverOrder(String dsrType, String ginNo, String orderNo, ValidationResult result) {
        deliverOrder(dsrType, ginNo, orderNo, null, result);
    }

    /**
     * Delivers the order. When the GIN is not offered, the delivery PJP's working/closing date is usually stale:
     * the flow prints the date update (orgaCode / distributor from scenarioData), waits for the tester, retries once.
     */
    public void deliverOrder(String dsrType, String ginNo, String orderNo, Map<String, String> scenarioData,
                             ValidationResult result) {
        selectDropdown(dsrTypeDropdown, dsrType);
        try {
            selectDropdown(ginNoDropdown, ginNo);
        } catch (RuntimeException ginMissing) {
            try {
                ManualStep.pause("GIN " + ginNo + " not offered on Cashmemo Status for PJP " + dsrType
                                + " - set the PJP's working date to today and closing date to yesterday",
                        List.of(pjpDateScript(dsrType, scenarioData)), Duration.ofMinutes(60));
            } catch (Exception e) {
                throw new IllegalStateException("GIN " + ginNo + " not offered on Cashmemo Status: " + e.getMessage(), e);
            }
            navigateToScreen(SCREEN_NAME, SCREEN_ID);
            selectDropdown(dsrTypeDropdown, dsrType);
            selectDropdown(ginNoDropdown, ginNo);
        }
        click(gridFilterCheckbox);

        type(docNoFilterInput, orderNo);
        waitForLoader();

        click(row1Checkbox);
        click(saveAllBtn);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoader();
    }

    // Placeholders stay in the script when the flow's ScenarioData has no orgaCode / distributor
    private static String pjpDateScript(String pjp, Map<String, String> scenarioData) {
        String orga = scenarioData == null ? null : scenarioData.get("orgaCode");
        String distributor = scenarioData == null ? null : scenarioData.get("distributor");
        LocalDate today = LocalDate.now();
        return "UPDATE public.snd_en_pjp_pjphead SET epjp_working_date='" + today + "', epjp_closing_date='"
                + today.minusDays(1) + "' WHERE porg_orgacode='" + (orga == null ? "<orga code>" : orga)
                + "' AND pbet_busentity_type='DIST' AND epp1_busent_phs_lvl1_code='"
                + (distributor == null ? "<distributor code>" : distributor) + "' AND epjp_pjpno='" + pjp + "';";
    }
}

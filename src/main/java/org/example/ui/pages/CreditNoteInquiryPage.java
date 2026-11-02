package org.example.ui.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Credit/Debit Note Inquiry: finds the credit note generated for an incentive (dates left at today).
 */
public class CreditNoteInquiryPage extends basePage {

    public static final String SCREEN_NAME = "Credit/Debit Note Inquiry";
    public static final String SCREEN_ID = "DYL_201502";
    public static final String INCENTIVE_REWARD_DOCUMENT_TYPE = "Credit Note Incentive Reward";

    private static final Duration ROW_WAIT = Duration.ofSeconds(10);

    private final By gridFilterCheckbox = By.id("gridFilterCheckbox");
    private final By documentTypeFilter = By.id("rowfilter_DocmtypeDesc");
    private final By commentsFilter = By.id("rowfilter_TXT__tfdh_comments");
    private final By firstRowComments = By.id("row_1_comments");
    private final By firstRowAmount = By.id("row_1_amount");
    private final By firstRowDocumentNo = By.id("row_1_document_no");

    public CreditNoteInquiryPage(WebDriver driver) {
        super(driver);
    }

    /**
     * Opens the screen and filters by document type and incentive code; the screen is reopened on each
     * attempt so a credit note created after the first load is fetched.
     * Returns {comments, amount, document no} of the incentive's row, or null if none appears within the timeout.
     */
    public List<String> findIncentiveCreditNote(String incentiveCode, Duration timeout) {
        // Exact code: "incentive Id: 12" must not match "incentive Id: 123"
        Pattern ourComment = incentiveCommentPattern(incentiveCode);
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        do {
            navigateToScreen(SCREEN_NAME, SCREEN_ID);
            // The checkbox toggles the filter row and the screen remembers its state, so click it only when the row is hidden
            waitForLoader();
            if (driver.findElements(documentTypeFilter).stream().noneMatch(WebElement::isDisplayed)) {
                click(gridFilterCheckbox);
            }
            type(documentTypeFilter, INCENTIVE_REWARD_DOCUMENT_TYPE);
            type(commentsFilter, incentiveCode);
            waitForLoader();
            if (waitForFirstRow(ourComment)) {
                click(firstRowComments);
                // The grid scrolls sideways, so off-screen cells have empty getText()
                return List.of(cellText(firstRowComments), cellText(firstRowAmount), cellText(firstRowDocumentNo));
            }
        } while (System.currentTimeMillis() < deadline);
        return null;
    }

    public static Pattern incentiveCommentPattern(String incentiveCode) {
        return Pattern.compile("incentive Id:\\s*" + Pattern.quote(incentiveCode) + "\\b");
    }

    // Row 1 can still show the unfiltered grid's first credit note until the filter applies
    private boolean waitForFirstRow(Pattern ourComment) {
        try {
            return new WebDriverWait(driver, ROW_WAIT).until(d -> !d.findElements(firstRowComments).isEmpty()
                    && ourComment.matcher(cellText(firstRowComments)).find());
        } catch (TimeoutException e) {
            return false;
        }
    }

    private String cellText(By cell) {
        return driver.findElement(cell).getAttribute("textContent").trim();
    }
}

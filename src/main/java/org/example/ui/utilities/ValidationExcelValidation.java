package org.example.ui.utilities;

import org.example.ui.pages.OrderBookingPage;
import org.example.ui.pages.basePage;
import org.openqa.selenium.WebDriver;

import java.util.Map;

public class ValidationExcelValidation {
    public static void validate(WebDriver driver,
                               String testId,
                               Map<String, Object> screen, Map<String, String> ScenarioData, ValidationResult result) {

        OrderBookingPage page = new OrderBookingPage(driver);

        page.navigateToScreen(PostUploadValidator.str(screen, "menuSearch"),PostUploadValidator.str(screen, "validateScreenId"));


        String actualMessage = page.orderBooking(ScenarioData);

        String expectedMessage = ScenarioData.get("ExpectedMessage");

        String expected = normalize(expectedMessage);
        String actual = normalize(actualMessage);

        if (actual.equals(expected)) {
            result.pass("Validation Message matched: " + actualMessage);
        } else {
            result.fail(
                    "Message mismatch. Expected=" +
                            expectedMessage +
                            " Actual=" +
                            actualMessage
            );
        }
        System.out.println("===== ValidationExcelValidation =====");
        System.out.println("Passed : " + result.passed);
        System.out.println("Passes : " + result.passes);
        System.out.println("Fails  : " + result.failures);
        System.out.println("=====================================");


    }
    private static String normalize(String text) {
        return text == null
                ? ""
                : text.trim().replaceAll("\\s+", " ");
    }
    }


package org.example.ui.pages;

import org.example.ui.utilities.ApiSession;
import org.example.ui.utilities.DriverFactory;
import org.example.ui.utilities.ToastHandles;
import org.example.ui.utilities.ValidationResult;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.util.Properties;

/**
 * Adhoc Executor: runs a background job group (e.g. CLP achievement 7020) for an organization.
 */
public class AdhocExecutorPage extends basePage {

    public static final String SCREEN_NAME = "adhoc executor";
    // The menu item's id on the BD back-office user's menu
    public static final String SCREEN_ID = "122";

    private final By organizationDropdown = By.id("organizations");
    private final By groupDropdown = By.id("groups");
    private final By groupSourceDropdown = By.id("groupDetails");
    private final By executeBtn = By.id("ExecuteBtn");
    private final By executeUserInput = By.id("UserName");
    private final By executePasswordInput = By.id("Password");
    private final By proceedBtn = By.xpath("//dx-button[.//span[normalize-space()='Proceed']]");

    // Opened by URL: the menu item's id differs between users
    private static final String SCREEN_PATH = "/ngui/content-master/mobility/adhoc-executor";

    public AdhocExecutorPage(WebDriver driver) {
        super(driver);
    }

    /**
     * Runs the job group as another user (e.g. BD "demo"), in a separate browser logged in as that user: the test
     * user is not allowed to execute some groups. The test user's browser is left as it was.
     */
    // Replaced by AdhocExecutorApiClient: opening this screen by URL as the node user lands back on the menu
    // public static String executeAs(WebDriver testDriver, String user, String password, String orga, String dist,
    //                                String organization, String group, String groupSource, ValidationResult result)
    //         throws Exception {
    //     String baseUrl = ApiSession.getBaseUrl(testDriver);
    //     Properties properties = DriverFactory.loadProperties();
    //     WebDriver jobDriver = DriverFactory.createDriver(properties, System.getProperty("java.io.tmpdir"));
    //     try {
    //         jobDriver.get(baseUrl + "/ngui");
    //         new loginPage(jobDriver).login(user, password, orga, dist);
    //         jobDriver.get(baseUrl + SCREEN_PATH);
    //         AdhocExecutorPage page = new AdhocExecutorPage(jobDriver);
    //         page.waitForLoader();
    //         return page.execute(organization, group, groupSource, user, password, result);
    //     } finally {
    //         jobDriver.quit();
    //     }
    // }

    /** Executes the job group, authorising with the execute user; returns the toast ("messages sent successfully"). */
    public String execute(String organization, String group, String groupSource, String executeUser, String executePassword,
                          ValidationResult result) {
        selectDropdown(organizationDropdown, organization);
        selectDropdown(groupDropdown, group);
        selectDropdown(groupSourceDropdown, groupSource);
        click(executeBtn);
        // Execute asks for the executing user before it sends the job
        type(executeUserInput, executeUser);
        type(executePasswordInput, executePassword);
        // The popup can re-render after opening and lose what was typed; fill it again if so
        if (value(executePasswordInput) == null || value(executePasswordInput).isEmpty()) {
            type(executeUserInput, executeUser);
            type(executePasswordInput, executePassword);
        }
        click(proceedBtn);
        String toast = ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoader();
        return toast;
    }
}

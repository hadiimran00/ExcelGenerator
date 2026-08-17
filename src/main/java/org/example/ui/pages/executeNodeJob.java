package org.example.ui.pages;

import org.example.ui.Main;
import org.example.ui.utilities.Event;
import org.example.ui.utilities.LoaderWait;
import org.example.ui.utilities.ValidationResult;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public class executeNodeJob extends basePage {

    // Pass the primary driver to super constructor so the framework context remains intact
    public executeNodeJob(WebDriver driver) {
        super(driver);
    }

    public void run(Map<String, String> scenarioData, ValidationResult result) throws Exception {

        // 1. Grab the active URL from the primary driver and resolve the Base URL dynamically
        String currentUrl = driver.getCurrentUrl();
        URI uri = URI.create(currentUrl);
        String baseUrl = uri.getScheme() + "://" + uri.getAuthority();

        // 2. Configure a completely isolated, headless background browser
        ChromeOptions options = new ChromeOptions();

// Disable password manager
        options.setExperimentalOption("prefs", Map.of(
                "credentials_enable_service", false,
                "profile.password_manager_enabled", false,
                "profile.password_manager_leak_detection", false
        ));

        options.addArguments("--disable-save-password-bubble");
        options.addArguments("--disable-features=PasswordManagerOnboarding");

        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

       options.addArguments("--headless=new"); // Runs silently without popping up
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        WebDriver tempDriver = null;

        try {
            // 3. Initialize the temporary driver
            tempDriver = new ChromeDriver(options);
            tempDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            WebDriverWait tempWait = new WebDriverWait(tempDriver, Duration.ofSeconds(20));

            // 4. Navigate to the target environment's login page
            tempDriver.get(baseUrl);

            // 5. Retrieve the credentials stored globally in Main during user-loop setup
            String nodeUser = Main.currentUser.get("nodeUsername");
            String nodePass = Main.currentUser.get("nodePassword");

            // 6. Bind LoginPage helper to our temporary, isolated browser instance
            loginPage tempLoginPage = new loginPage(tempDriver);
            tempLoginPage.login(nodeUser, nodePass);

            // 7. Perform page navigation on the background session
            tempLoginPage.navigateToScreen("Node executor", "NODE_JOB_EXECUTOR");

            // 8. Organization
            WebElement organization = tempWait.until(
                    ExpectedConditions.elementToBeClickable(By.id("organizations")));
            organization.clear();
            organization.sendKeys(scenarioData.getOrDefault("Organization", "U002"));

            // 9. Group
            WebElement group = tempWait.until(
                    ExpectedConditions.elementToBeClickable(By.id("groups")));
            group.clear();
            group.sendKeys(scenarioData.getOrDefault("Group", "JLB5"));

            // 10. Group Details
            WebElement groupDetails = tempWait.until(
                    ExpectedConditions.elementToBeClickable(By.id("groupDetails")));
            groupDetails.clear();
            groupDetails.sendKeys(scenarioData.getOrDefault("GroupDetails", "LB12"));

            // 11. Trigger Execution
            Event.robustClick(tempDriver, By.id("ExecuteBtn"));

            // 12. Handle the authorization details popup
            WebElement userName = tempWait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.id("UserName")));
            userName.clear();
            userName.sendKeys(scenarioData.getOrDefault("ExecuteUser", "integrator"));

            WebElement password = tempWait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.id("Password")));
            password.clear();
            password.sendKeys(scenarioData.getOrDefault("ExecutePassword", "123456"));

            // 13. Proceed with execution
            Event.robustClick(tempDriver, By.xpath("//dx-button[.//span[normalize-space()='Proceed']]"));

            // 14. Pass the background driver to the loader wait
            LoaderWait.waitForLoaderToDisappear(tempDriver);

          //  result.pass("Node Executor job executed successfully.");

        } catch (Exception e) {
            result.fail("Node Executor failed: " + e.getMessage());
            throw e;

        } finally {
            // 15. ALWAYS kill the background browser session completely to prevent memory leaks
            if (tempDriver != null) {
                tempDriver.quit();
            }
        }
    }
}
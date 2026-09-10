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

    public executeNodeJob(WebDriver driver) {
        super(driver);
    }

    public void run(Map<String, String> scenarioData, ValidationResult result) throws Exception {

        // 1. Grab active URL from primary driver and resolve Base URL
        String currentUrl = driver.getCurrentUrl();
        URI uri = URI.create(currentUrl);
        String baseUrl = uri.getScheme() + "://" + uri.getAuthority();

        // 2. Configure isolated background browser options
        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("prefs", Map.of(
                "credentials_enable_service", false,
                "profile.password_manager_enabled", false,
                "profile.password_manager_leak_detection", false
        ));

        options.addArguments("--disable-save-password-bubble");
        options.addArguments("--disable-features=PasswordManagerOnboarding");
        options.addArguments("--headless=new");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        WebDriver tempDriver = null;

        try {
            // 3. Initialize background WebDriver session
            tempDriver = new ChromeDriver(options);
            tempDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            WebDriverWait tempWait = new WebDriverWait(tempDriver, Duration.ofSeconds(20));

            // 4. Navigate to base login page
            tempDriver.get(baseUrl);

            // 5. Retrieve node credentials safely via UserConfig getters
            String nodeUser = Main.currentUser.getNodeUsername();
            String nodePass = Main.currentUser.getNodePassword();

            // 6. Bind loginPage helper to isolated browser
            loginPage tempLoginPage = new loginPage(tempDriver);
            tempLoginPage.login(nodeUser, nodePass);

            // 7. Navigate to target screen
            tempLoginPage.navigateToScreen("Node executor", "NODE_JOB_EXECUTOR");

            // 8. Input Organization
            WebElement organization = tempWait.until(
                    ExpectedConditions.elementToBeClickable(By.id("organizations")));
            organization.clear();
            organization.sendKeys(scenarioData.getOrDefault("Organization", "U002"));

            // 9. Input Group
            WebElement group = tempWait.until(
                    ExpectedConditions.elementToBeClickable(By.id("groups")));
            group.clear();
            group.sendKeys(scenarioData.getOrDefault("Group", "JLB5"));

            // 10. Input Group Details
            WebElement groupDetails = tempWait.until(
                    ExpectedConditions.elementToBeClickable(By.id("groupDetails")));
            groupDetails.clear();
            groupDetails.sendKeys(scenarioData.getOrDefault("GroupDetails", "LB12"));

            // 11. Trigger Execution
            Event.robustClick(tempDriver, By.id("ExecuteBtn"));

            // 12. Fill Authorization Credentials
            WebElement userName = tempWait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.id("UserName")));
            userName.clear();
            userName.sendKeys(scenarioData.getOrDefault("ExecuteUser", "integrator"));

            WebElement password = tempWait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.id("Password")));
            password.clear();
            password.sendKeys(scenarioData.getOrDefault("ExecutePassword", "123456"));

            // 13. Confirm execution
            Event.robustClick(tempDriver, By.xpath("//dx-button[.//span[normalize-space()='Proceed']]"));

            // 14. Synchronize DevExpress loaders
            LoaderWait.waitForLoaderToDisappear(tempDriver);

        } catch (Exception e) {
            result.fail("Node Executor failed: " + e.getMessage());
            throw e;

        } finally {
            // 15. Teardown background browser session
            if (tempDriver != null) {
                try {
                    tempDriver.quit();
                } catch (Exception ignored) {}
            }
        }
    }
}
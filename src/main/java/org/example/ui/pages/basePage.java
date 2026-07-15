package org.example.ui.pages;

import org.example.ui.utilities.Event;
import org.example.ui.utilities.LoaderWait;
import org.example.ui.utilities.PostUploadValidator;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;

import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.databind.type.LogicalType.Map;

import static org.example.ui.utilities.PostUploadValidator.str;


public abstract class basePage {

    protected final WebDriver driver;
    protected final WebDriverWait wait;



    protected basePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    protected WebElement find(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    public void click(By locator) {

        // Find the element
        Event.robustClick(driver, locator);


    }

    protected void type(By locator, String text) {

        System.out.println("Typing into locator: " + locator);
        System.out.println("Text: " + text);

        try {
            WebElement element = find(locator);
            System.out.println("Element found");

            element.clear();
            System.out.println("Field cleared");

            // Re-find the element in case the DOM refreshed
            element = find(locator);
            System.out.println("Element found again");

            System.out.println("Current HTML: " + element.getAttribute("outerHTML"));

            element.sendKeys(text);
            System.out.println("Text entered successfully");

        } catch (StaleElementReferenceException e) {

            System.out.println("Element became stale. Retrying...");

            WebElement element = find(locator);

            element.clear();
            element = find(locator);
            element.sendKeys(text);

            System.out.println("Retry successful");
        }
    }
    protected String value(By locator) {
        return find(locator).getAttribute("value");
    }

    protected void scrollTo(By locator) {

        WebElement element = find(locator);

        ((JavascriptExecutor) driver)
                .executeScript(
                        "arguments[0].scrollIntoView({block:'center'});",
                        element);
    }

    protected void waitForLoader() {

        By loader = By.cssSelector(".dx-loadindicator-wrapper");

        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(2));

            // Wait until loader is actually visible on the UI (not just present)
            WebElement visibleLoader = shortWait.until(ExpectedConditions.visibilityOfElementLocated(loader));

            // Only log if loader appeared
            if (visibleLoader != null) {


                // Wait until loader disappears
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
                wait.until(ExpectedConditions.invisibilityOfElementLocated(loader));


            }

        } catch (TimeoutException e) {
            // Loader never appeared → safe to continue

        }

    }
    public void selectDropdown(By locator, String value) {

        waitForLoader();
        WebElement field = wait.until(
                ExpectedConditions.elementToBeClickable(locator));

        field.click();
        field.clear();
        field.sendKeys(value);
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        waitForLoader();
        wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("(//div[@id='dropdown-content']//*[contains(text(),'" + value + "')])[1]")
        )).click();

        waitForLoader();
    }


    public void navigateToScreen(Map<String, Object> screen) {

        String menuSearch = PostUploadValidator.str(screen, "menuSearch");
        String screenId = PostUploadValidator.str(screen, "validateScreenId");
        try {

            // Click hamburger if menu is collapsed
            try {
//                wait.until(ExpectedConditions.visibilityOfElementLocated(
//                        By.id("menurollin")));
                Event.robustClick(driver, By.id("menurollin"));
            } catch (NoSuchElementException e) {
                wait.until(ExpectedConditions.visibilityOfElementLocated(
                        By.cssSelector("input[placeholder='Search Here']")));
            }

            // Type screen name
            WebElement searchBox = wait.until(
                    ExpectedConditions.elementToBeClickable(
                            By.cssSelector("input[placeholder='Search Here']")));

            searchBox.clear();
            searchBox.sendKeys(menuSearch);

            Thread.sleep(400);

            // Open screen
            wait.until(ExpectedConditions
                            .elementToBeClickable(By.id(screenId)));
                    Event.robustClick(driver, By.id(screenId));

            waitForLoader();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Navigation interrupted.", e);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Could not navigate to screen: " + menuSearch, e);
        }

    }
    public boolean isCheckboxChecked(String Code) {
        try {
            WebElement checkbox = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//tr[contains(@class,'dx-data-row')][.//td[normalize-space()='" + Code + "']]//div[@role='checkbox']")
                    )
            );

            String value = checkbox.findElement(By.cssSelector("input[type='hidden']"))
                    .getAttribute("value");

            System.out.println("Selling Category Code = " + Code);
            System.out.println("Checkbox value = " + value);

            return "true".equalsIgnoreCase(value);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    public void acceptAlertIfPresent(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(3));
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());

            System.out.println("Accepting alert: {}" + alert.getText());

            alert.accept();
        } catch (TimeoutException ignored) {
        }
    }

    public static Map<String, Object> findScreenByTestId(
            List<Map<String, Object>> screens,
            String testId,
            String resourcesFolder) {

        if (testId == null || testId.isBlank()) {
            throw new RuntimeException(
                    "ScenarioData is missing a required testId");
        }

        for (Map<String, Object> screen : screens) {
            if (testId.equals(screen.get("testID"))) {
                // Return a shallow copy so we don't mutate the shared config list
                // when resolving templatePath to an absolute path.
                Map<String, Object> resolved = new LinkedHashMap<>(screen);

                String templatePath = (String) resolved.get("templatePath");
                if (templatePath != null) {
                    String rootPath = System.getProperty("user.dir") + File.separator + resourcesFolder;
                    resolved.put("templatePath", Paths.get(rootPath, templatePath).toString());
                }

                return resolved;
            }
        }

        throw new RuntimeException("No screen found in config with testID: " + testId);
    }

}



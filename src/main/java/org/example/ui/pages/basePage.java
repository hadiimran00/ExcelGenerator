package org.example.ui.pages;

import org.example.ui.Main;
import org.example.ui.utilities.Event;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.*;

import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


import static org.example.ui.utilities.LoaderWait.waitForLoaderToDisappear;
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
        try {
            Event.robustClick(driver, locator);
        } catch (Exception e) {
            System.out.println("First click failed. Retrying...");

            try {
                Event.robustClick(driver, locator);
            } catch (Exception retryException) {
                throw new RuntimeException(
                        "Failed to click element after retry: " + locator,
                        retryException
                );
            }
        }
    }

    protected void type(By locator, String text) {
        System.out.println("Typing into locator: " + locator);
        System.out.println("Text: " + text);

        try {
            WebElement element = find(locator);
            System.out.println("Element found");

            element.clear();
            System.out.println("Field cleared");

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
                .executeScript("arguments[0].scrollIntoView({block:'center'});", element);
    }

    protected void waitForLoader() {
        By loader = By.cssSelector(".dx-loadindicator-wrapper");
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(2));
            WebElement visibleLoader = shortWait.until(ExpectedConditions.visibilityOfElementLocated(loader));

            if (visibleLoader != null) {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
                wait.until(ExpectedConditions.invisibilityOfElementLocated(loader));
            }
        } catch (TimeoutException e) {
            // Loader never appeared → safe to continue
        }
    }

//    public void selectDropdown(By locator, String value) {
//        waitForLoader();
//        WebElement field = wait.until(ExpectedConditions.elementToBeClickable(locator));
//
//        field.click();
//
//        // Ignore clear if element is read-only or doesn't support clearing
//        try {
//            field.clear();
//        } catch (org.openqa.selenium.InvalidElementStateException e) {
//            // Element is read-only or non-editable; ignore and continue
//        }
//        wait.until(ExpectedConditions.elementToBeClickable(locator));
//        Actions actions = new Actions(driver);
//        actions.click(field);
//        for (char ch : value.toCharArray()) {
//            actions.sendKeys(String.valueOf(ch)).pause(Duration.ofMillis(100));
//        }
//        actions.perform();
//
//        try {
//            Thread.sleep(500);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//        waitForLoader();
//        wait.until(ExpectedConditions.elementToBeClickable(
//                By.xpath("(//div[@id='dropdown-content']//*[contains(text(),'" + value + "')])[1]")
//        )).click();
//
//        waitForLoader();
//    }

    public void selectDropdown(By locator, String value) {
        waitForLoader();

        // 1. Wait for and locate the dropdown field
        WebElement field = wait.until(ExpectedConditions.elementToBeClickable(locator));
        Event.robustClick(driver, locator);

        // 2. Clear existing input if editable
        try {
            field.clear();
        } catch (org.openqa.selenium.InvalidElementStateException e) {
            // Ignored if element is read-only or non-editable
        }

        wait.until(ExpectedConditions.elementToBeClickable(locator));

        // 3. Ensure proper focus before typing
        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].focus();", field);
        } catch (Exception e) {
            // Non-critical focus fallback
        }

        // 4. Type character-by-character to trigger dynamic JS autocomplete/filtering
        Actions actions = new Actions(driver);
        actions.click(field);
        for (char ch : value.toCharArray()) {
            actions.sendKeys(String.valueOf(ch)).pause(Duration.ofMillis(100));
        }
        actions.perform();

        // 5. Debounce pause for network search / list rendering
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        waitForLoader();

        // 6. Define primary and fallback locators
        By primaryLocator = By.xpath(
                "(//div[@id='dropdown-content']//*[contains(text(), " + escapeXPathValue(value) + ")])[1]"
        );

        By fallbackLocator = By.xpath(
                "(//div[@id='dropdown-content']//*[" +
                        "(contains(@class, 'dx-item-content') or contains(@class, 'dx-item') or not(contains(@class, 'dx-list-items')))" +
                        " and contains(normalize-space(.), " + escapeXPathValue(value) + ")])[1]"
        );

        // 7. Try primary locator first using robustClick; if it fails/times out, execute fallback
        try {
            wait.until(ExpectedConditions.elementToBeClickable(primaryLocator));
            Event.robustClick(driver, primaryLocator);
        } catch (Exception e) {
            // Fallback for DevExtreme elements or nested containers where text() fails
            wait.until(ExpectedConditions.elementToBeClickable(fallbackLocator));
            Event.robustClick(driver, fallbackLocator);
        }

        waitForLoader();
    }
    /**
     * Escapes quotes in XPath string arguments safely.
     */
    private String escapeXPathValue(String value) {
        if (!value.contains("'")) return "'" + value + "'";
        if (!value.contains("\"")) return "\"" + value + "\"";
        String[] parts = value.split("'", -1);
        StringBuilder xpath = new StringBuilder("concat(");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) xpath.append(", \"'\", ");
            xpath.append("'").append(parts[i]).append("'");
        }
        xpath.append(")");
        return xpath.toString();
    }
    public void navigateToScreen(String menuSearch, String screenId) {
        try {
            try {
                Event.robustClick(driver, By.id("menurollin"));
            } catch (NoSuchElementException e) {
                wait.until(ExpectedConditions.visibilityOfElementLocated(
                        By.cssSelector("input[placeholder='Search Here']")));
            }

            WebElement searchBox = wait.until(
                    ExpectedConditions.elementToBeClickable(
                            By.cssSelector("input[placeholder='Search Here']")));

            searchBox.clear();
            searchBox.sendKeys(menuSearch);

            Thread.sleep(400);

            wait.until(ExpectedConditions.elementToBeClickable(By.id(screenId)));
            Event.robustClick(driver, By.id(screenId));

            waitForLoader();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Navigation interrupted.", e);
        } catch (Exception e) {
            throw new RuntimeException("Could not navigate to screen: " + menuSearch, e);
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
            System.out.println("Accepting alert: " + alert.getText());
            alert.accept();
        } catch (TimeoutException ignored) {
        }
    }

    // Keeps static context as it has no dependency on instance members
    public static Map<String, Object> findScreenByTestId(
            List<Map<String, Object>> screens,
            String testId,
            String resourcesFolder) {

        if (testId == null || testId.isBlank()) {
            throw new RuntimeException("ScenarioData is missing a required testId");
        }

        for (Map<String, Object> screen : screens) {
            if (testId.equals(screen.get("testID"))) {
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
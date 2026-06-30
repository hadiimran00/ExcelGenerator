package org.example.ui.pages;

import org.example.ui.utilities.LoaderWait;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;

import java.time.Duration;



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

    protected void click(By locator) {
        wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
    }

    protected void type(By locator, String text) {
        WebElement element = find(locator);
        element.clear();
        element.sendKeys(text);
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
        LoaderWait.waitForLoaderToDisappear(driver);
    }

    public void navigateToScreen(String screenName, String menuItemId) {

        try {

            // Click hamburger if menu is collapsed
            try {
                wait.until(ExpectedConditions
                                .elementToBeClickable(By.id("menurollin")))
                        .click();
            } catch (NoSuchElementException e) {
                wait.until(ExpectedConditions.visibilityOfElementLocated(
                        By.cssSelector("input[placeholder='Search Here']")));
            }

            // Type screen name
            WebElement searchBox = wait.until(
                    ExpectedConditions.elementToBeClickable(
                            By.cssSelector("input[placeholder='Search Here']")));

            searchBox.clear();
            searchBox.sendKeys(screenName);

            Thread.sleep(400);

            // Open screen
            wait.until(ExpectedConditions
                            .elementToBeClickable(By.id(menuItemId)))
                    .click();

            waitForLoader();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Navigation interrupted.", e);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Could not navigate to screen: " + screenName, e);
        }
    }

    }

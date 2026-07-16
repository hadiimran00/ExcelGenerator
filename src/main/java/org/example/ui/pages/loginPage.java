package org.example.ui.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import java.util.List;

public class loginPage extends basePage {

    public loginPage(WebDriver driver) {
        super(driver);
    }

    /**
     * Basic login with credentials only (reusable in Node Executor, Saga scripts, etc.)
     */
    public void login(String username, String password) {
        login(username, password, null, null);
    }

    /**
     * Full login workflow with optional Organization and Distributor selections.
     */
    public void login(String username, String password, String orga, String dist) {
        try {
            // Reuses inherited 'type' from basePage which has built-in wait & stale-element handling
            type(By.id("a3"), username);
            type(By.id("a4"), password);
            click(By.cssSelector("button[type='submit']"));
            waitForLoader();

            List<WebElement> selectBoxes = driver.findElements(By.id("selectBox1"));

            // Select Organization if provided
            if (!selectBoxes.isEmpty() && selectBoxes.get(0).isDisplayed() && orga != null) {
                type(By.id("selectBox1"), orga);
                try {
                    WebElement item = wait.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("(//div[@id='dropdown-content']//*[contains(text(), '" + orga + "')])[1]")
                    ));
                    item.click();
                } catch (Exception e) {
                    System.out.println("⚠️ No dropdown item found for Organization: " + orga);
                }
                click(By.id("proceedBtn"));
                waitForLoader();
            }

            // Select Distributor if provided
            if (!selectBoxes.isEmpty() && dist != null) {
                type(By.id("selectBox1"), dist);
                try {
                    WebElement item = wait.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("(//div[@id='dropdown-content']//*[contains(text(), '" + dist + "')])[1]")
                    ));
                    item.click();
                } catch (Exception e) {
                    System.out.println("⚠️ No dropdown item found for Distributor: " + dist);
                }
                click(By.id("proceedBtn"));
                waitForLoader();
            }
        } catch (Exception e) {
            throw new RuntimeException("Login sequence failed for: " + username, e);
        }
    }
}
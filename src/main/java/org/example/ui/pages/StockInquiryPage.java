package org.example.ui.pages;

import org.apache.logging.log4j.Logger;
import org.example.ui.utilities.Event;
import org.example.ui.utilities.LoggerUtil;
import org.example.ui.utilities.ScreenshotService;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;

public class StockInquiryPage extends basePage {
    private static final Logger logger = LoggerUtil.getLogger(StockInquiryPage.class);
    private final WebDriverWait wait;

    public StockInquiryPage(WebDriver driver) {
        super(driver);
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    public void searchSku(String sku) {
        logger.info("🔍 Querying stock status for SKU: {}", sku);

        // 1. Refresh or load the inquiry workspace view
        Event.robustClick(driver, By.id("showInquiryRefresh"));

        // 2. Toggle the grid filter visibility row if required by the UI state
        Event.robustClick(driver, By.id("gridFilterCheckbox"));

        By searchBoxLocator = By.id("rowfilter_asyd");
        By wrhsLocator = By.id("rowfilter_warehousedesc");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        try {
            // 3. Locate and populate the SKU inline column filter
            WebElement searchBox = wait.until(ExpectedConditions.elementToBeClickable(searchBoxLocator));
            searchBox.clear();
            searchBox.sendKeys(sku);

            // 4. Locate and populate the Warehouse inline column filter
            WebElement wrhsBox = wait.until(ExpectedConditions.elementToBeClickable(wrhsLocator));
            wrhsBox.clear();
            wrhsBox.sendKeys("Sound Stock");

            // 5. Submit the combined DevExtreme inline filters using the Enter key
            wrhsBox.sendKeys(org.openqa.selenium.Keys.ENTER);
            logger.info("🔎 Grid filters applied successfully -> SKU: [{}], Warehouse: [Sound Stock]", sku);

            // Allow virtualized DOM components a brief moment to finish redrawing rows
            Thread.sleep(1000);

        } catch (Exception e) {
            logger.error("❌ Stock Inquiry grid filtering execution failed: {}", e.getMessage());
            ScreenshotService.takeScreenshot(driver, "stock_search_failed_" + sku);
            throw new RuntimeException("Automation step blocked: Grid filter components failed to synchronize.", e);
        }
    }
    public double readPhysicalStock() {
        By physicalStockLocator = By.id("row_1_in_cs");
        WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(physicalStockLocator));
        String text = element.getText().trim();
        logger.info("📊 Read Physical Stock: {}", text);
        return Double.parseDouble(text);
    }
}
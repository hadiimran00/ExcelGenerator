package org.example.ui.utilities;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;

import java.net.URI;

/** Logged-in browser session details shared by the API clients (OrderBookingApiClient, TargetServiceApiClient). */
public class ApiSession {

    public static String getSessionCookieValue(WebDriver driver) {
        Cookie cookie = driver.manage().getCookieNamed("SESSIONID");
        if (cookie == null) {
            throw new RuntimeException("SESSIONID cookie not found in browser session.");
        }
        return cookie.getValue();
    }

    public static String getBaseUrl(WebDriver driver) {
        URI uri = URI.create(driver.getCurrentUrl());
        return uri.getScheme() + "://" + uri.getAuthority();
    }
}

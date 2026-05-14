package org.example.ui.utilities;

import java.util.ArrayList;
import java.util.List;

public class ValidationResult {

    public final String screenName;
    public boolean passed = true;
    public final List<String> passes   = new ArrayList<>();
    public final List<String> failures = new ArrayList<>();

    public ValidationResult(String screenName) {
        this.screenName = screenName;
    }

    public void pass(String detail) {
        passes.add(detail);
    }

    public void fail(String detail) {
        passed = false;
        failures.add(detail);
    }

    public void logSummary(org.apache.logging.log4j.Logger logger) {
        if (passed) {
            logger.info("✅ Validation PASSED [{}] — {} checks OK", screenName, passes.size());
        } else {
            logger.info("❌ Validation FAILED [{}]", screenName);
            failures.forEach(f -> logger.info("   ↳ {}", f));
        }
    }
}
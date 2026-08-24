package top.trumeet.mipushframework.main;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppConfigurationUtilsFakeSuggestionTest {
    @Test
    public void eligibilityDependsOnlyOnInstallTypeAndSelfIdentity() {
        assertTrue(AppConfigurationUtils.isFakeSuggestionEligible(true, false));
        assertFalse(AppConfigurationUtils.isFakeSuggestionEligible(false, false));
        assertFalse(AppConfigurationUtils.isFakeSuggestionEligible(true, true));
        assertFalse(AppConfigurationUtils.isFakeSuggestionEligible(false, true));
    }
}

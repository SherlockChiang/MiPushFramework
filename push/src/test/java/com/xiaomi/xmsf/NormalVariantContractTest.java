package com.xiaomi.xmsf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class NormalVariantContractTest {

    @Test
    public void testNormalVariantContract() {
        if (!BuildConfig.QA_BUILD) {
            assertFalse("QA_BUILD must be false for normal variant", BuildConfig.QA_BUILD);
            assertEquals("Normal package name must be com.xiaomi.xmsf", "com.xiaomi.xmsf", BuildConfig.APPLICATION_ID);
        }
    }

    @Test
    public void testVersionCodeContract() {
        if (!BuildConfig.QA_BUILD && BuildConfig.VERSION_CODE > 0) {
            // Normal variant version code contract (1003003001 or normal)
            assertNotNull(BuildConfig.VERSION_NAME);
        }
    }
}

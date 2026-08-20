package com.xiaomi.xmsf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class NormalVariantContractTest {

    @Test
    public void testNormalVariantContract() {
        assertEquals("Normal package name must be com.xiaomi.xmsf", "com.xiaomi.xmsf", BuildConfig.APPLICATION_ID);
    }

    @Test
    public void testVersionCodeContract() {
        // Normal variant version code contract (1003003001 or normal)
        if (BuildConfig.VERSION_CODE > 0) {
            assertNotNull(BuildConfig.VERSION_NAME);
        }
    }
}

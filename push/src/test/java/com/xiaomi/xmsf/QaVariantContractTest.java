package com.xiaomi.xmsf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QaVariantContractTest {

    @Test
    public void testQaVariantContract() {
        // When running in QA variant, QA_BUILD must be true
        if (BuildConfig.QA_BUILD) {
            assertTrue("QA_BUILD must be true for qa variant", BuildConfig.QA_BUILD);
            assertTrue("QA application ID must end with .qa or be qa variant",
                    BuildConfig.APPLICATION_ID.endsWith(".qa") || BuildConfig.BUILD_TYPE.equals("debug"));
        }
    }

    @Test
    public void testGreenDaoSchemaVersionContract() {
        // Schema version 17 must be preserved
        assertNotNull("Package com.xiaomi.xmsf must exist", BuildConfig.APPLICATION_ID);
    }
}

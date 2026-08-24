package com.xiaomi.push.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.elvishew.xlog.XLog;
import com.xiaomi.xmpush.thrift.ActionType;

import org.junit.Before;
import org.junit.Test;

public class MIPushEventProcessorAspectPolicyTest {
    @Before
    public void initializeLogging() {
        XLog.init();
    }

    @Test
    public void onlyRegistrationBypassesAppAlivePolicy() {
        assertTrue(MIPushEventProcessorAspect.bypassesAppAliveCheck(
                ActionType.Registration));
        assertFalse(MIPushEventProcessorAspect.bypassesAppAliveCheck(
                ActionType.SendMessage));
        assertFalse(MIPushEventProcessorAspect.bypassesAppAliveCheck(
                ActionType.Notification));
        assertFalse(MIPushEventProcessorAspect.bypassesAppAliveCheck(null));
    }
}

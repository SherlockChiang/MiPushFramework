package top.trumeet.mipush.provider.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult;

import org.junit.Test;

import top.trumeet.mipush.provider.entities.Event;
import top.trumeet.mipush.provider.entities.RegisteredApplication;

public class RegistrationEvidenceResolverTest {
    @Test
    public void verifiedRuntimeSuccessRepairsStaleSdkState() {
        assertEquals(
                RegisteredApplication.RegisteredType.Registered,
                RegistrationEvidenceResolver.resolve(true, true, true, false, false));
    }

    @Test
    public void persistedSdkStatePrecedesOlderNegativeEvents() {
        assertEquals(
                RegisteredApplication.RegisteredType.Unregistered,
                RegistrationEvidenceResolver.resolve(true, true, false, true, true));
        assertEquals(
                RegisteredApplication.RegisteredType.Registered,
                RegistrationEvidenceResolver.resolve(false, true, false, false, true));
    }

    @Test
    public void explicitUnregistrationRejectsLateReceiveEvidence() {
        assertEquals(
                RegisteredApplication.RegisteredType.Unregistered,
                RegistrationEvidenceResolver.resolve(true, true, false, true, false));
    }

    @Test
    public void latestEventDecidesWhenCurrentSdkStateIsSilent() {
        assertEquals(
                RegisteredApplication.RegisteredType.Registered,
                RegistrationEvidenceResolver.resolve(false, false, true, false, false));
        assertEquals(
                RegisteredApplication.RegisteredType.Unregistered,
                RegistrationEvidenceResolver.resolve(false, false, false, false, true));
        assertEquals(
                RegisteredApplication.RegisteredType.NotRegistered,
                RegistrationEvidenceResolver.resolve(false, false, false, false, false));
    }

    @Test
    public void unregistrationIsNegativeControlEvidence() {
        assertEquals(
                RegistrationEvidenceResolver.EventEvidence.NEGATIVE,
                RegistrationEvidenceResolver.classifyEvent(Event.Type.UnRegistration, null));
    }

    @Test
    public void receiveTimeMustBeStrictlyNewerThanControlEvidence() {
        assertTrue(RegistrationEvidenceResolver.isReceiveEvidenceNewer(20L, 10L));
        assertFalse(RegistrationEvidenceResolver.isReceiveEvidenceNewer(10L, 10L));
        assertFalse(RegistrationEvidenceResolver.isReceiveEvidenceNewer(9L, 10L));
        assertTrue(RegistrationEvidenceResolver.isReceiveEvidenceNewer(1L, null));
        assertFalse(RegistrationEvidenceResolver.isReceiveEvidenceNewer(0L, null));
    }

    @Test
    public void persistedUnregisteredSetSurvivesColdStartParsing() {
        assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList(
                        "com.example.first", "com.example.second")),
                RegistrationEvidenceResolver.parsePersistedPackageSet(
                        " com.example.first,,com.example.second,com.example.first "));
        assertTrue(RegistrationEvidenceResolver.parsePersistedPackageSet("").isEmpty());
        assertTrue(RegistrationEvidenceResolver.parsePersistedPackageSet(null).isEmpty());
    }

    @Test
    public void registrationResultMustDecodeAndSetErrorCode() {
        assertEquals(
                RegistrationEvidenceResolver.EventEvidence.UNKNOWN,
                RegistrationEvidenceResolver.classifyEvent(
                        Event.Type.RegistrationResult, null));
        assertEquals(
                RegistrationEvidenceResolver.EventEvidence.UNKNOWN,
                RegistrationEvidenceResolver.classifyEvent(
                        Event.Type.RegistrationResult,
                        new XmPushActionRegistrationResult()));
        assertEquals(
                RegistrationEvidenceResolver.EventEvidence.POSITIVE,
                RegistrationEvidenceResolver.classifyEvent(
                        Event.Type.RegistrationResult,
                        new XmPushActionRegistrationResult().setErrorCode(0)));
        assertEquals(
                RegistrationEvidenceResolver.EventEvidence.NEGATIVE,
                RegistrationEvidenceResolver.classifyEvent(
                        Event.Type.RegistrationResult,
                        new XmPushActionRegistrationResult().setErrorCode(1)));
    }
}

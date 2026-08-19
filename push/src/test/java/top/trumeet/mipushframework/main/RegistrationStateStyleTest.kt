package top.trumeet.mipushframework.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.trumeet.mipush.provider.entities.RegisteredApplication.RegisteredType
import top.trumeet.mipush.provider.entities.RegisteredApplication.ServiceProbeState

class RegistrationStateStyleTest {
    @Test
    fun registeredStateNeverReportsMissingServices() {
        assertFalse(
            RegistrationStateStyle.shouldShowMissingServices(
                RegisteredType.Registered,
                existServices = false,
            ),
        )
    }

    @Test
    fun unregisteredStateReportsMissingServices() {
        assertTrue(
            RegistrationStateStyle.shouldShowMissingServices(
                RegisteredType.Unregistered,
                existServices = false,
            ),
        )
    }

    @Test
    fun availableServicesSuppressDiagnostic() {
        assertFalse(
            RegistrationStateStyle.shouldShowMissingServices(
                RegisteredType.NotRegistered,
                existServices = true,
            ),
        )
    }

    @Test
    fun unknownServiceProbeDoesNotReportMissingServices() {
        assertFalse(
            RegistrationStateStyle.shouldShowMissingServices(
                RegisteredType.NotRegistered,
                ServiceProbeState.UNKNOWN,
            ),
        )
        assertFalse(
            RegistrationStateStyle.shouldShowMissingServices(
                RegisteredType.Unregistered,
                ServiceProbeState.UNKNOWN,
            ),
        )
    }
}

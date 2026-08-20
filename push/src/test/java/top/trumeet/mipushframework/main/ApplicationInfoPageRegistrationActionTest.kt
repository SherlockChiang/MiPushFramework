package top.trumeet.mipushframework.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.trumeet.mipush.provider.entities.RegisteredApplication.RegisteredType

class ApplicationInfoPageRegistrationActionTest {

    @Test
    fun notRegisteredOffersExplicitRegistration() {
        assertEquals(
            RegistrationAction.REGISTER,
            registrationActionFor(RegisteredType.NotRegistered),
        )
    }

    @Test
    fun registrationErrorOffersReregistration() {
        assertEquals(
            RegistrationAction.REREGISTER,
            registrationActionFor(RegisteredType.Unregistered),
        )
    }

    @Test
    fun registeredAppDoesNotOfferDestructiveRegistrationAction() {
        assertNull(registrationActionFor(RegisteredType.Registered))
    }
}

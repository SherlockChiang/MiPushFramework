package top.trumeet.mipushframework.main

import org.junit.Assert.assertEquals
import org.junit.Test
import top.trumeet.mipush.provider.entities.RegisteredApplication
import top.trumeet.mipushframework.main.subpage.ApplicationFilter
import top.trumeet.mipushframework.main.subpage.filterApplicationsForDisplay

class ApplicationListFilterTest {
    private fun app(type: Int, services: Boolean): RegisteredApplication =
        RegisteredApplication(null, "pkg$type$services", RegisteredApplication.Type.ASK, true, type, "app")
            .also { it.existServices = services }

    @Test
    fun categoriesUseRegistrationFactEvenWhenServiceProbeFails() {
        val registered = app(RegisteredApplication.RegisteredType.Registered, false)
        assertEquals(listOf(registered), filterApplicationsForDisplay(listOf(registered), ApplicationFilter.Registered))
        assertEquals(emptyList<RegisteredApplication>(), filterApplicationsForDisplay(listOf(registered), ApplicationFilter.Unregistered))
    }

    @Test
    fun problemAndNotRegisteredRemainSeparate() {
        val problem = app(RegisteredApplication.RegisteredType.Unregistered, false)
        val notRegistered = app(RegisteredApplication.RegisteredType.NotRegistered, true)
        val all = listOf(problem, notRegistered)
        assertEquals(listOf(problem), filterApplicationsForDisplay(all, ApplicationFilter.Unregistered))
        assertEquals(listOf(notRegistered), filterApplicationsForDisplay(all, ApplicationFilter.NotRegistered))
    }
}

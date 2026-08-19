package top.trumeet.mipushframework.main

import org.junit.Assert.assertEquals
import org.junit.Test
import top.trumeet.mipush.provider.entities.RegisteredApplication.ServiceProbeState
import top.trumeet.mipushframework.main.subpage.ApplicationPageOperation
import top.trumeet.mipushframework.utils.MiPushManifestChecker.ServiceCheckResult

class ApplicationPageServiceProbeMappingTest {
    @Test
    fun checkerResultMapsToUiProbeState() {
        assertEquals(
            ServiceProbeState.PRESENT,
            ApplicationPageOperation.mapServiceCheckResult(ServiceCheckResult.PRESENT),
        )
        assertEquals(
            ServiceProbeState.MISSING,
            ApplicationPageOperation.mapServiceCheckResult(ServiceCheckResult.MISSING),
        )
        assertEquals(
            ServiceProbeState.UNKNOWN,
            ApplicationPageOperation.mapServiceCheckResult(ServiceCheckResult.UNKNOWN),
        )
        assertEquals(
            ServiceProbeState.UNKNOWN,
            ApplicationPageOperation.mapServiceCheckResult(null),
        )
    }
}

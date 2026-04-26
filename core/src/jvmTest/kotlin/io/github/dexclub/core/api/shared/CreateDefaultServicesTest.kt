package io.github.dexclub.core.api.shared

import kotlin.test.Test
import kotlin.test.assertNotNull

class CreateDefaultServicesTest {
    @Test
    fun createsAllServiceEntries() {
        val services = createDefaultServices()

        assertNotNull(services.workspace)
        assertNotNull(services.dex)
        assertNotNull(services.resource)
    }
}

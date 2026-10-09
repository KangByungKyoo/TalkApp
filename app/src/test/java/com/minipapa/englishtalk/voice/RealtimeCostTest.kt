package com.minipapa.englishtalk.voice

import org.junit.Assert.*
import org.junit.Test

class RealtimeCostTest {
    @Test fun mixedModalitiesSubtractCachedInputInsteadOfDoubleCharging() {
        val usage = TokenBreakdown(1000, 2000, 500, 1000, 100, 300)
        assertEquals(0.01687, RealtimeCost.estimateUsd("gpt-realtime-2.1-mini", usage)!!, 1e-10)
    }
    @Test fun unknownPricingMissingDetailsAndInvalidCacheAreNotReportedAsFree() {
        assertNull(RealtimeCost.estimateUsd("unknown", TokenBreakdown(1, 1, 0, 0, 1, 1)))
        assertNull(RealtimeCost.estimateUsd("gpt-realtime-2.1-mini", null))
        assertNull(RealtimeCost.estimateUsd("gpt-realtime-2.1-mini", TokenBreakdown(1, 1, 2, 0, 1, 1)))
    }
}

package com.example.dairyflow2

import com.example.dairyflow2.core.InvoiceLineInput
import com.example.dairyflow2.core.calculateDeliveryEarnings
import com.example.dairyflow2.core.calculateInvoiceTotal
import com.example.dairyflow2.data.model.DeliveryStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BillingRulesTest {
    @Test
    fun invoiceTotalExcludesSkippedDeliveriesAndAddsPending() {
        val total = calculateInvoiceTotal(
            lines = listOf(
                InvoiceLineInput("milk", "Cow Milk", 6200, 2.0, DeliveryStatus.Done),
                InvoiceLineInput("milk", "Cow Milk", 6200, 2.0, DeliveryStatus.Skipped),
                InvoiceLineInput("milk", "Cow Milk", 6200, 1.5, DeliveryStatus.Done),
            ),
            previousPendingPaise = 5000,
        )

        assertEquals(21700, total.subtotalPaise)
        assertEquals(26700, total.totalPaise)
        assertEquals(3.5, total.lines.single().quantity, 0.001)
    }

    @Test
    fun earningsUseCompletedDeliveryCountAndConfiguredRate() {
        assertEquals(4500, calculateDeliveryEarnings(completedDeliveries = 3, ratePerDeliveryPaise = 1500))
        assertEquals(0, calculateDeliveryEarnings(completedDeliveries = -1, ratePerDeliveryPaise = 1500))
    }
}

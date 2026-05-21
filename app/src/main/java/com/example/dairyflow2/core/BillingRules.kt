package com.example.dairyflow2.core

import com.example.dairyflow2.data.model.DeliveryStatus

data class InvoiceLineInput(
    val productId: String,
    val productName: String,
    val unitPricePaise: Long,
    val quantityPerDelivery: Double,
    val status: DeliveryStatus,
)

data class InvoiceLineTotal(
    val productId: String,
    val description: String,
    val quantity: Double,
    val unitPricePaise: Long,
    val amountPaise: Long,
)

data class InvoiceTotal(
    val lines: List<InvoiceLineTotal>,
    val subtotalPaise: Long,
    val previousPendingPaise: Long,
    val totalPaise: Long,
)

fun calculateInvoiceTotal(lines: List<InvoiceLineInput>, previousPendingPaise: Long): InvoiceTotal {
    val invoiceLines = lines
        .filter { it.status == DeliveryStatus.Done }
        .groupBy { it.productId }
        .map { (_, productLines) ->
            val first = productLines.first()
            val quantity = productLines.sumOf { it.quantityPerDelivery }
            val amount = kotlin.math.round(quantity * first.unitPricePaise).toLong()
            InvoiceLineTotal(
                productId = first.productId,
                description = "${first.productName} delivered days",
                quantity = quantity,
                unitPricePaise = first.unitPricePaise,
                amountPaise = amount,
            )
        }
    val subtotal = invoiceLines.sumOf { it.amountPaise }
    return InvoiceTotal(
        lines = invoiceLines,
        subtotalPaise = subtotal,
        previousPendingPaise = previousPendingPaise,
        totalPaise = subtotal + previousPendingPaise,
    )
}

fun calculateDeliveryEarnings(completedDeliveries: Int, ratePerDeliveryPaise: Long): Long =
    completedDeliveries.coerceAtLeast(0) * ratePerDeliveryPaise


package com.example.dairyflow2.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class UserRole(val wireName: String) {
    Admin("admin"),
    DeliveryBoy("delivery_boy");

    companion object {
        fun fromWire(value: String?): UserRole =
            entries.firstOrNull { it.wireName == value } ?: Admin
    }
}

enum class DeliveryStatus(val wireName: String) {
    Pending("pending"),
    Done("done"),
    Skipped("skipped");

    companion object {
        fun fromWire(value: String?): DeliveryStatus =
            entries.firstOrNull { it.wireName == value } ?: Pending
    }
}

@Serializable
data class Profile(
    val id: String,
    val role: UserRole,
    val fullName: String,
    val email: String = "",
    val phone: String = "",
    val rating: Double = 0.0,
    val totalDeliveries: Int = 0,
    val onTimeRate: Double = 0.0,
    val streak: Int = 0,
    val ratePerDeliveryPaise: Long = 1500,
    val isActive: Boolean = true,
)

@Serializable
data class Product(
    val id: String,
    val name: String,
    val unit: String,
    val pricePaise: Long,
    val isActive: Boolean = true,
)

@Serializable
data class Customer(
    val id: String,
    val name: String,
    val phone: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val productId: String,
    val quantity: Double,
    val isActive: Boolean = true,
)

@Serializable
data class Assignment(
    val id: String,
    val customerId: String,
    val deliveryBoyId: String,
    val productId: String,
    val quantity: Double,
    val isActive: Boolean = true,
)

@Serializable
data class Delivery(
    val id: String,
    val assignmentId: String,
    val customerId: String,
    val deliveryBoyId: String,
    val productId: String,
    val deliveryDate: String,
    val status: DeliveryStatus,
    val completedAt: String? = null,
    val skippedReason: String? = null,
)

@Serializable
data class Invoice(
    val id: String,
    val customerId: String,
    val month: String,
    val subtotalPaise: Long,
    val previousPendingPaise: Long,
    val totalPaise: Long,
    val status: String,
)

@Serializable
data class InvoiceItem(
    val id: String,
    val invoiceId: String,
    val productId: String,
    val description: String,
    val quantity: Double,
    val unitPricePaise: Long,
    val amountPaise: Long,
)

@Serializable
data class Payment(
    val id: String,
    val invoiceId: String,
    val customerId: String,
    val amountPaise: Long,
    val paidAt: String,
)

@Serializable
data class DashboardSummary(
    val revenuePaise: Long,
    val totalProducts: Int,
    val activeCustomers: Int,
    val deliveryStaff: Int,
    val pendingDeliveries: Int,
)

@Serializable
data class DeliveryCard(
    val delivery: Delivery,
    val customer: Customer,
    val product: Product,
    val deliveryBoy: Profile?,
)

@Serializable
data class EarningsSummary(
    val completedDeliveries: Int,
    val ratePerDeliveryPaise: Long,
    val totalPaise: Long,
)

@Serializable
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val profile: Profile,
)

@Serializable
data class SupabaseProfileDto(
    val id: String,
    val role: String,
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val rating: Double? = null,
    @SerialName("total_deliveries") val totalDeliveries: Int? = null,
    @SerialName("on_time_rate") val onTimeRate: Double? = null,
    val streak: Int? = null,
    @SerialName("rate_per_delivery_paise") val ratePerDeliveryPaise: Long? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
) {
    fun toProfile(): Profile = Profile(
        id = id,
        role = UserRole.fromWire(role),
        fullName = fullName.orEmpty().ifBlank { email.orEmpty() },
        email = email.orEmpty(),
        phone = phone.orEmpty(),
        rating = rating ?: 0.0,
        totalDeliveries = totalDeliveries ?: 0,
        onTimeRate = onTimeRate ?: 0.0,
        streak = streak ?: 0,
        ratePerDeliveryPaise = ratePerDeliveryPaise ?: 1500,
        isActive = isActive ?: true,
    )
}


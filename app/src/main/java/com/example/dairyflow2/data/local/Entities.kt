package com.example.dairyflow2.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.dairyflow2.data.model.Assignment
import com.example.dairyflow2.data.model.Customer
import com.example.dairyflow2.data.model.Delivery
import com.example.dairyflow2.data.model.DeliveryStatus
import com.example.dairyflow2.data.model.Invoice
import com.example.dairyflow2.data.model.InvoiceItem
import com.example.dairyflow2.data.model.Payment
import com.example.dairyflow2.data.model.Product
import com.example.dairyflow2.data.model.Profile
import com.example.dairyflow2.data.model.UserRole

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val role: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val rating: Double,
    val totalDeliveries: Int,
    val onTimeRate: Double,
    val streak: Int,
    val ratePerDeliveryPaise: Long,
    val isActive: Boolean,
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val unit: String,
    val pricePaise: Long,
    val isActive: Boolean,
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val productId: String,
    val quantity: Double,
    val isActive: Boolean,
)

@Entity(tableName = "assignments")
data class AssignmentEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val deliveryBoyId: String,
    val productId: String,
    val quantity: Double,
    val isActive: Boolean,
)

@Entity(tableName = "deliveries")
data class DeliveryEntity(
    @PrimaryKey val id: String,
    val assignmentId: String,
    val customerId: String,
    val deliveryBoyId: String,
    val productId: String,
    val deliveryDate: String,
    val status: String,
    val completedAt: String?,
    val skippedReason: String?,
)

@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val month: String,
    val subtotalPaise: Long,
    val previousPendingPaise: Long,
    val totalPaise: Long,
    val status: String,
)

@Entity(tableName = "invoice_items")
data class InvoiceItemEntity(
    @PrimaryKey val id: String,
    val invoiceId: String,
    val productId: String,
    val description: String,
    val quantity: Double,
    val unitPricePaise: Long,
    val amountPaise: Long,
)

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey val id: String,
    val invoiceId: String,
    val customerId: String,
    val amountPaise: Long,
    val paidAt: String,
)

@Entity(tableName = "pending_mutations")
data class PendingMutationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deliveryId: String,
    val status: String,
    val skippedReason: String?,
    val createdAt: String,
)

fun Profile.toEntity() = ProfileEntity(
    id = id,
    role = role.wireName,
    fullName = fullName,
    email = email,
    phone = phone,
    rating = rating,
    totalDeliveries = totalDeliveries,
    onTimeRate = onTimeRate,
    streak = streak,
    ratePerDeliveryPaise = ratePerDeliveryPaise,
    isActive = isActive,
)

fun ProfileEntity.toDomain() = Profile(
    id = id,
    role = UserRole.fromWire(role),
    fullName = fullName,
    email = email,
    phone = phone,
    rating = rating,
    totalDeliveries = totalDeliveries,
    onTimeRate = onTimeRate,
    streak = streak,
    ratePerDeliveryPaise = ratePerDeliveryPaise,
    isActive = isActive,
)

fun Product.toEntity() = ProductEntity(id, name, unit, pricePaise, isActive)

fun ProductEntity.toDomain() = Product(id, name, unit, pricePaise, isActive)

fun Customer.toEntity() = CustomerEntity(id, name, phone, address, latitude, longitude, productId, quantity, isActive)

fun CustomerEntity.toDomain() = Customer(id, name, phone, address, latitude, longitude, productId, quantity, isActive)

fun Assignment.toEntity() = AssignmentEntity(id, customerId, deliveryBoyId, productId, quantity, isActive)

fun AssignmentEntity.toDomain() = Assignment(id, customerId, deliveryBoyId, productId, quantity, isActive)

fun Delivery.toEntity() = DeliveryEntity(
    id = id,
    assignmentId = assignmentId,
    customerId = customerId,
    deliveryBoyId = deliveryBoyId,
    productId = productId,
    deliveryDate = deliveryDate,
    status = status.wireName,
    completedAt = completedAt,
    skippedReason = skippedReason,
)

fun DeliveryEntity.toDomain() = Delivery(
    id = id,
    assignmentId = assignmentId,
    customerId = customerId,
    deliveryBoyId = deliveryBoyId,
    productId = productId,
    deliveryDate = deliveryDate,
    status = DeliveryStatus.fromWire(status),
    completedAt = completedAt,
    skippedReason = skippedReason,
)

fun Invoice.toEntity() = InvoiceEntity(id, customerId, month, subtotalPaise, previousPendingPaise, totalPaise, status)

fun InvoiceEntity.toDomain() = Invoice(id, customerId, month, subtotalPaise, previousPendingPaise, totalPaise, status)

fun InvoiceItem.toEntity() = InvoiceItemEntity(id, invoiceId, productId, description, quantity, unitPricePaise, amountPaise)

fun InvoiceItemEntity.toDomain() = InvoiceItem(id, invoiceId, productId, description, quantity, unitPricePaise, amountPaise)

fun Payment.toEntity() = PaymentEntity(id, invoiceId, customerId, amountPaise, paidAt)

fun PaymentEntity.toDomain() = Payment(id, invoiceId, customerId, amountPaise, paidAt)


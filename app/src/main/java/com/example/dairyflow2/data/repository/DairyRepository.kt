package com.example.dairyflow2.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.dairyflow2.core.currentMonthKey
import com.example.dairyflow2.core.todayKey
import com.example.dairyflow2.data.local.AssignmentEntity
import com.example.dairyflow2.data.local.CustomerEntity
import com.example.dairyflow2.data.local.DairyDao
import com.example.dairyflow2.data.local.DeliveryEntity
import com.example.dairyflow2.data.local.InvoiceEntity
import com.example.dairyflow2.data.local.InvoiceItemEntity
import com.example.dairyflow2.data.local.PendingMutationEntity
import com.example.dairyflow2.data.local.ProductEntity
import com.example.dairyflow2.data.local.ProfileEntity
import com.example.dairyflow2.data.local.toDomain
import com.example.dairyflow2.data.local.toEntity
import com.example.dairyflow2.data.model.Assignment
import com.example.dairyflow2.data.model.Customer
import com.example.dairyflow2.data.model.DashboardSummary
import com.example.dairyflow2.data.model.Delivery
import com.example.dairyflow2.data.model.DeliveryCard
import com.example.dairyflow2.data.model.DeliveryStatus
import com.example.dairyflow2.data.model.EarningsSummary
import com.example.dairyflow2.data.model.Invoice
import com.example.dairyflow2.data.model.Payment
import com.example.dairyflow2.data.model.Product
import com.example.dairyflow2.data.model.Profile
import com.example.dairyflow2.data.model.UserRole
import com.example.dairyflow2.data.remote.SupabaseConfig
import com.example.dairyflow2.data.remote.SupabaseRestClient
import com.example.dairyflow2.notification.AppNotifier
import com.example.dairyflow2.sync.OfflineSyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class DairyRepository(
    private val context: Context,
    private val dao: DairyDao,
    private val remote: SupabaseRestClient,
    private val sessionStore: SessionStore,
    private val notifier: AppNotifier,
) {
    suspend fun bootstrap() {
        dao.seedIfEmpty(
            profiles = demoProfiles,
            products = demoProducts,
            customers = demoCustomers,
            assignments = demoAssignments,
            deliveries = demoDeliveries,
            invoices = demoInvoices,
            items = demoInvoiceItems,
        )
    }

    fun observeSession(): Flow<StoredSession?> = sessionStore.session

    suspend fun login(email: String, password: String): Profile {
        demoProfileFor(email, password)?.let { profile ->
            saveSession("demo-access-token", "demo-refresh-token", profile)
            return profile
        }

        sessionStore.findLocalAccount(email, password)?.let { profile ->
            saveSession("local-access-token", "local-refresh-token", profile)
            return profile
        }

        val session = remote.signIn(email, password)
        saveSession(session.accessToken, session.refreshToken, session.profile)
        return session.profile
    }

    suspend fun loginWithDemo(role: UserRole): Profile {
        val profile = demoProfile(role)
        saveSession("demo-access-token", "demo-refresh-token", profile)
        return profile
    }

    suspend fun createAccount(email: String, password: String, role: UserRole, fullName: String, phone: String): Profile {
        val profile = Profile(
            id = uuid(),
            role = role,
            fullName = fullName.trim(),
            email = email.trim(),
            phone = phone.trim(),
            rating = if (role == UserRole.DeliveryBoy) 4.5 else 0.0,
        )
        sessionStore.saveLocalAccount(profile, password)
        saveSession("local-access-token", "local-refresh-token", profile)
        return profile
    }

    suspend fun logout() {
        sessionStore.clear()
    }

    fun observePendingSyncCount(): Flow<Int> = dao.observePendingMutationCount()

    fun observeAdminSummary(): Flow<DashboardSummary> = combine(
        dao.observeProducts(),
        dao.observeCustomers(),
        dao.observeActiveProfiles(),
        dao.observeDeliveries(),
        dao.observeInvoices(),
    ) { products, customers, profiles, deliveries, invoices ->
        DashboardSummary(
            revenuePaise = invoices.filter { it.status == "paid" }.sumOf { it.totalPaise },
            totalProducts = products.count { it.isActive },
            activeCustomers = customers.count { it.isActive },
            deliveryStaff = profiles.count { it.role == UserRole.DeliveryBoy.wireName && it.isActive },
            pendingDeliveries = deliveries.count { it.status == DeliveryStatus.Pending.wireName },
        )
    }

    fun observeProducts(): Flow<List<Product>> =
        dao.observeProducts().map { list -> list.map { it.toDomain() } }

    fun observeCustomers(): Flow<List<Customer>> =
        dao.observeCustomers().map { list -> list.map { it.toDomain() } }

    fun observeStaff(): Flow<List<Profile>> =
        dao.observeActiveProfiles().map { list ->
            list.map { it.toDomain() }.filter { it.role == UserRole.DeliveryBoy }
        }

    fun observeAssignments(): Flow<List<Assignment>> =
        dao.observeAssignments().map { list -> list.map { it.toDomain() } }

    fun observeInvoices(): Flow<List<Invoice>> =
        dao.observeInvoices().map { list -> list.map { it.toDomain() } }

    fun observeAdminDeliveries(date: String = todayKey()): Flow<List<DeliveryCard>> =
        combineCards(dao.observeDeliveriesForDate(date))

    fun observeTodayDeliveries(deliveryBoyId: String, date: String = todayKey()): Flow<List<DeliveryCard>> =
        combineCards(dao.observeDeliveriesForStaffOnDate(deliveryBoyId, date)).map { cards ->
            cards.optimizedRoute()
        }

    fun observeHistory(deliveryBoyId: String): Flow<List<DeliveryCard>> =
        combineCards(dao.observeDeliveryHistory(deliveryBoyId))

    fun observeEarnings(deliveryBoyId: String): Flow<EarningsSummary> =
        combine(dao.observeDeliveryHistory(deliveryBoyId), dao.observeActiveProfiles()) { deliveries, profiles ->
            val profile = profiles.firstOrNull { it.id == deliveryBoyId }?.toDomain()
            val completed = deliveries.count { it.status == DeliveryStatus.Done.wireName }
            val rate = profile?.ratePerDeliveryPaise ?: 1500
            EarningsSummary(completed, rate, completed * rate)
        }

    suspend fun upsertProduct(name: String, unit: String, pricePaise: Long, id: String = uuid()) {
        dao.upsertProduct(Product(id, name, unit, pricePaise, true).toEntity())
    }

    suspend fun softDeleteProduct(id: String) {
        dao.softDeleteProduct(id)
    }

    suspend fun upsertCustomer(
        name: String,
        phone: String,
        address: String,
        latitude: Double,
        longitude: Double,
        productId: String,
        quantity: Double,
        id: String = uuid(),
    ) {
        dao.upsertCustomer(Customer(id, name, phone, address, latitude, longitude, productId, quantity, true).toEntity())
    }

    suspend fun softDeleteCustomer(id: String) {
        dao.softDeleteCustomer(id)
    }

    suspend fun upsertAssignment(customerId: String, deliveryBoyId: String, productId: String, quantity: Double, id: String = uuid()) {
        val assignment = Assignment(id, customerId, deliveryBoyId, productId, quantity, true)
        dao.upsertAssignment(assignment.toEntity())
        val delivery = Delivery(
            id = uuid(),
            assignmentId = assignment.id,
            customerId = customerId,
            deliveryBoyId = deliveryBoyId,
            productId = productId,
            deliveryDate = todayKey(),
            status = DeliveryStatus.Pending,
        )
        dao.upsertDelivery(delivery.toEntity())
        val customer = dao.getCustomer(customerId)?.name ?: "New customer"
        notifier.showAssignmentNotification("New delivery assigned", customer)
    }

    suspend fun softDeleteAssignment(id: String) {
        dao.softDeleteAssignment(id)
    }

    suspend fun createUser(email: String, password: String, role: UserRole, fullName: String, phone: String) {
        val token = sessionStore.session.first()?.accessToken.orEmpty()
        remote.createUser(token, email, password, role, fullName, phone)
        dao.upsertProfile(
            Profile(
                id = uuid(),
                role = role,
                fullName = fullName,
                email = email,
                phone = phone,
                rating = if (role == UserRole.DeliveryBoy) 4.5 else 0.0,
            ).toEntity(),
        )
    }

    suspend fun markDelivery(deliveryId: String, status: DeliveryStatus, reason: String? = null) {
        val completedAt = if (status == DeliveryStatus.Done) Instant.now().toString() else null
        val skippedReason = if (status == DeliveryStatus.Skipped) reason.orEmpty().ifBlank { "Skipped by delivery staff" } else null
        dao.updateDeliveryStatus(deliveryId, status.wireName, completedAt, skippedReason)
        dao.insertPendingMutation(
            PendingMutationEntity(
                deliveryId = deliveryId,
                status = status.wireName,
                skippedReason = skippedReason,
                createdAt = Instant.now().toString(),
            ),
        )
        enqueueSync()
    }

    suspend fun markInvoicePaid(invoice: Invoice) {
        val now = Instant.now().toString()
        val payment = Payment(uuid(), invoice.id, invoice.customerId, invoice.totalPaise, now)
        dao.upsertPayment(payment.toEntity())
        dao.updateInvoiceStatus(invoice.id, "paid")
        val token = sessionStore.session.first()?.accessToken.orEmpty()
        runCatching { remote.markInvoicePaid(token, invoice.id, invoice.customerId, invoice.totalPaise) }
    }

    suspend fun syncPendingMutations(): Boolean {
        if (!SupabaseConfig.isConfigured) {
            dao.pendingMutations().forEach { dao.deletePendingMutation(it.id) }
            return true
        }
        val token = sessionStore.session.first()?.accessToken.orEmpty()
        if (token.isBlank()) return false

        dao.pendingMutations().forEach { mutation ->
            val delivery = dao.getDelivery(mutation.deliveryId) ?: run {
                dao.deletePendingMutation(mutation.id)
                return@forEach
            }
            remote.updateDeliveryStatus(token, delivery.id, mutation.status, mutation.skippedReason)
            dao.deletePendingMutation(mutation.id)
        }
        return true
    }

    private fun combineCards(deliveries: Flow<List<DeliveryEntity>>): Flow<List<DeliveryCard>> =
        combine(
            deliveries,
            dao.observeCustomers(),
            dao.observeProducts(),
            dao.observeActiveProfiles(),
        ) { deliveryEntities, customers, products, profiles ->
            val customerMap = customers.associateBy { it.id }
            val productMap = products.associateBy { it.id }
            val profileMap = profiles.associateBy { it.id }
            deliveryEntities.mapNotNull { delivery ->
                val customer = customerMap[delivery.customerId]?.toDomain() ?: return@mapNotNull null
                val product = productMap[delivery.productId]?.toDomain() ?: return@mapNotNull null
                DeliveryCard(
                    delivery = delivery.toDomain(),
                    customer = customer,
                    product = product,
                    deliveryBoy = profileMap[delivery.deliveryBoyId]?.toDomain(),
                )
            }
        }

    private fun List<DeliveryCard>.optimizedRoute(): List<DeliveryCard> {
        if (size < 3) return sortedBy { it.customer.name }
        val remaining = toMutableList()
        val ordered = mutableListOf<DeliveryCard>()
        var currentLat = remaining.first().customer.latitude
        var currentLng = remaining.first().customer.longitude
        while (remaining.isNotEmpty()) {
            val nearest = remaining.minBy {
                distanceKm(currentLat, currentLng, it.customer.latitude, it.customer.longitude)
            }
            ordered += nearest
            remaining -= nearest
            currentLat = nearest.customer.latitude
            currentLng = nearest.customer.longitude
        }
        return ordered
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun enqueueSync() {
        val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("delivery-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private fun uuid(): String = UUID.randomUUID().toString()

    private suspend fun saveSession(accessToken: String, refreshToken: String, profile: Profile) {
        bootstrap()
        sessionStore.save(accessToken, refreshToken, profile)
        dao.upsertProfile(profile.toEntity())
    }

    private fun demoProfileFor(email: String, password: String): Profile? {
        if (password != "password") return null
        return when (email.trim().lowercase()) {
            "admin@dairyflow.local" -> demoProfile(UserRole.Admin)
            "staff@dairyflow.local" -> demoProfile(UserRole.DeliveryBoy)
            else -> null
        }
    }

    private fun demoProfile(role: UserRole): Profile =
        if (role == UserRole.DeliveryBoy) {
            Profile(
                id = "staff-1",
                role = UserRole.DeliveryBoy,
                fullName = "Ravi Delivery",
                email = "staff@dairyflow.local",
                phone = "+91 98765 43210",
                rating = 4.8,
                totalDeliveries = 214,
                onTimeRate = 96.0,
                streak = 18,
            )
        } else {
            Profile(
                id = "admin-1",
                role = UserRole.Admin,
                fullName = "Dairy Owner",
                email = "admin@dairyflow.local",
                phone = "+91 90000 00000",
            )
        }

    private val demoProfiles = listOf(
        ProfileEntity("admin-1", "admin", "Dairy Owner", "admin@dairyflow.local", "+91 90000 00000", 0.0, 0, 0.0, 0, 0, true),
        ProfileEntity("staff-1", "delivery_boy", "Ravi Delivery", "staff@dairyflow.local", "+91 98765 43210", 4.8, 214, 96.0, 18, 1500, true),
        ProfileEntity("staff-2", "delivery_boy", "Meena Delivery", "meena@dairyflow.local", "+91 98765 00002", 4.6, 189, 93.0, 11, 1500, true),
    )

    private val demoProducts = listOf(
        ProductEntity("milk-cow", "Cow Milk", "L", 6200, true),
        ProductEntity("milk-buffalo", "Buffalo Milk", "L", 7800, true),
        ProductEntity("paneer", "Paneer", "kg", 42000, true),
    )

    private val demoCustomers = listOf(
        CustomerEntity("cust-1", "Ananya Sharma", "+91 99880 11111", "Sector 18, Noida", 28.5708, 77.3261, "milk-cow", 2.0, true),
        CustomerEntity("cust-2", "Vikram Singh", "+91 99880 22222", "Sector 62, Noida", 28.6280, 77.3649, "milk-buffalo", 1.5, true),
        CustomerEntity("cust-3", "Priya Mehta", "+91 99880 33333", "Indirapuram, Ghaziabad", 28.6366, 77.3693, "milk-cow", 1.0, true),
    )

    private val demoAssignments = listOf(
        AssignmentEntity("assign-1", "cust-1", "staff-1", "milk-cow", 2.0, true),
        AssignmentEntity("assign-2", "cust-2", "staff-1", "milk-buffalo", 1.5, true),
        AssignmentEntity("assign-3", "cust-3", "staff-2", "milk-cow", 1.0, true),
    )

    private val demoDeliveries = listOf(
        DeliveryEntity("del-1", "assign-1", "cust-1", "staff-1", "milk-cow", todayKey(), "pending", null, null),
        DeliveryEntity("del-2", "assign-2", "cust-2", "staff-1", "milk-buffalo", todayKey(), "pending", null, null),
        DeliveryEntity("del-3", "assign-3", "cust-3", "staff-2", "milk-cow", todayKey(), "pending", null, null),
        DeliveryEntity("del-old-1", "assign-1", "cust-1", "staff-1", "milk-cow", "2026-05-20", "done", "2026-05-20T07:30:00Z", null),
        DeliveryEntity("del-old-2", "assign-2", "cust-2", "staff-1", "milk-buffalo", "2026-05-20", "skipped", null, "Customer requested pause"),
    )

    private val demoInvoices = listOf(
        InvoiceEntity("inv-1", "cust-1", currentMonthKey(), 372000, 0, 372000, "pending"),
        InvoiceEntity("inv-2", "cust-2", currentMonthKey(), 327600, 7800, 335400, "pending"),
    )

    private val demoInvoiceItems = listOf(
        InvoiceItemEntity("item-1", "inv-1", "milk-cow", "Cow Milk - delivered days", 60.0, 6200, 372000),
        InvoiceItemEntity("item-2", "inv-2", "milk-buffalo", "Buffalo Milk - delivered days", 42.0, 7800, 327600),
    )
}

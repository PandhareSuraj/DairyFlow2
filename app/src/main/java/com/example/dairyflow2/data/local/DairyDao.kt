package com.example.dairyflow2.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DairyDao {
    @Query("select count(*) from products")
    suspend fun productCount(): Int

    @Query("select * from profiles")
    fun observeProfiles(): Flow<List<ProfileEntity>>

    @Query("select * from profiles where isActive = 1")
    fun observeActiveProfiles(): Flow<List<ProfileEntity>>

    @Query("select * from profiles where id = :id limit 1")
    suspend fun getProfile(id: String): ProfileEntity?

    @Upsert
    suspend fun upsertProfile(profile: ProfileEntity)

    @Upsert
    suspend fun upsertProfiles(profiles: List<ProfileEntity>)

    @Query("select * from products order by name")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("select * from products where id = :id limit 1")
    suspend fun getProduct(id: String): ProductEntity?

    @Upsert
    suspend fun upsertProduct(product: ProductEntity)

    @Upsert
    suspend fun upsertProducts(products: List<ProductEntity>)

    @Query("update products set isActive = 0 where id = :id")
    suspend fun softDeleteProduct(id: String)

    @Query("select * from customers order by name")
    fun observeCustomers(): Flow<List<CustomerEntity>>

    @Query("select * from customers where id = :id limit 1")
    suspend fun getCustomer(id: String): CustomerEntity?

    @Upsert
    suspend fun upsertCustomer(customer: CustomerEntity)

    @Upsert
    suspend fun upsertCustomers(customers: List<CustomerEntity>)

    @Query("update customers set isActive = 0 where id = :id")
    suspend fun softDeleteCustomer(id: String)

    @Query("select * from assignments order by id")
    fun observeAssignments(): Flow<List<AssignmentEntity>>

    @Upsert
    suspend fun upsertAssignment(assignment: AssignmentEntity)

    @Upsert
    suspend fun upsertAssignments(assignments: List<AssignmentEntity>)

    @Query("update assignments set isActive = 0 where id = :id")
    suspend fun softDeleteAssignment(id: String)

    @Query("select * from deliveries order by deliveryDate desc")
    fun observeDeliveries(): Flow<List<DeliveryEntity>>

    @Query("select * from deliveries where deliveryDate = :date order by id")
    fun observeDeliveriesForDate(date: String): Flow<List<DeliveryEntity>>

    @Query("select * from deliveries where deliveryBoyId = :deliveryBoyId and deliveryDate = :date order by id")
    fun observeDeliveriesForStaffOnDate(deliveryBoyId: String, date: String): Flow<List<DeliveryEntity>>

    @Query("select * from deliveries where deliveryBoyId = :deliveryBoyId and status != 'pending' order by deliveryDate desc")
    fun observeDeliveryHistory(deliveryBoyId: String): Flow<List<DeliveryEntity>>

    @Query("select * from deliveries where id = :id limit 1")
    suspend fun getDelivery(id: String): DeliveryEntity?

    @Upsert
    suspend fun upsertDelivery(delivery: DeliveryEntity)

    @Upsert
    suspend fun upsertDeliveries(deliveries: List<DeliveryEntity>)

    @Query("update deliveries set status = :status, completedAt = :completedAt, skippedReason = :skippedReason where id = :id")
    suspend fun updateDeliveryStatus(id: String, status: String, completedAt: String?, skippedReason: String?)

    @Query("select * from invoices order by month desc, status, id")
    fun observeInvoices(): Flow<List<InvoiceEntity>>

    @Upsert
    suspend fun upsertInvoice(invoice: InvoiceEntity)

    @Upsert
    suspend fun upsertInvoices(invoices: List<InvoiceEntity>)

    @Query("update invoices set status = :status where id = :invoiceId")
    suspend fun updateInvoiceStatus(invoiceId: String, status: String)

    @Query("select * from invoice_items where invoiceId = :invoiceId")
    fun observeInvoiceItems(invoiceId: String): Flow<List<InvoiceItemEntity>>

    @Upsert
    suspend fun upsertInvoiceItem(item: InvoiceItemEntity)

    @Upsert
    suspend fun upsertInvoiceItems(items: List<InvoiceItemEntity>)

    @Query("select * from payments order by paidAt desc")
    fun observePayments(): Flow<List<PaymentEntity>>

    @Upsert
    suspend fun upsertPayment(payment: PaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingMutation(mutation: PendingMutationEntity): Long

    @Query("select * from pending_mutations order by createdAt")
    suspend fun pendingMutations(): List<PendingMutationEntity>

    @Query("select count(*) from pending_mutations")
    fun observePendingMutationCount(): Flow<Int>

    @Query("delete from pending_mutations where id = :id")
    suspend fun deletePendingMutation(id: Long)

    @Transaction
    suspend fun seedIfEmpty(
        profiles: List<ProfileEntity>,
        products: List<ProductEntity>,
        customers: List<CustomerEntity>,
        assignments: List<AssignmentEntity>,
        deliveries: List<DeliveryEntity>,
        invoices: List<InvoiceEntity>,
        items: List<InvoiceItemEntity>,
    ) {
        if (productCount() == 0) {
            upsertProfiles(profiles)
            upsertProducts(products)
            upsertCustomers(customers)
            upsertAssignments(assignments)
            upsertDeliveries(deliveries)
            upsertInvoices(invoices)
            upsertInvoiceItems(items)
        }
    }
}


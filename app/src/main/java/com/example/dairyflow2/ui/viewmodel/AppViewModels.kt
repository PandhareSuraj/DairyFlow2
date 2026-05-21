package com.example.dairyflow2.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.dairyflow2.core.UiState
import com.example.dairyflow2.core.todayKey
import com.example.dairyflow2.data.model.DeliveryStatus
import com.example.dairyflow2.data.model.Invoice
import com.example.dairyflow2.data.model.UserRole
import com.example.dairyflow2.data.repository.DairyRepository
import com.example.dairyflow2.data.repository.ServiceLocator
import com.example.dairyflow2.data.repository.StoredSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ServiceLocator.repository(application)

    val session: StateFlow<StoredSession?> = repository.observeSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pendingSyncCount = repository.observePendingSyncCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val loginState = kotlinx.coroutines.flow.MutableStateFlow<UiState<Unit>>(UiState.Success(Unit))

    init {
        viewModelScope.launch { repository.bootstrap() }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            loginState.value = UiState.Loading
            runCatching { repository.login(email, password) }
                .onSuccess { loginState.value = UiState.Success(Unit) }
                .onFailure { loginState.value = UiState.Error(it.toAuthMessage()) }
        }
    }

    fun loginWithDemo(role: UserRole) {
        viewModelScope.launch {
            loginState.value = UiState.Loading
            runCatching { repository.loginWithDemo(role) }
                .onSuccess { loginState.value = UiState.Success(Unit) }
                .onFailure { loginState.value = UiState.Error(it.toAuthMessage()) }
        }
    }

    fun createAccount(email: String, password: String, role: UserRole, fullName: String, phone: String) {
        viewModelScope.launch {
            loginState.value = UiState.Loading
            runCatching { repository.createAccount(email, password, role, fullName, phone) }
                .onSuccess { loginState.value = UiState.Success(Unit) }
                .onFailure { loginState.value = UiState.Error(it.toAuthMessage()) }
        }
    }

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }

    companion object {
        fun factory(application: Application) = viewModelFactory { AuthViewModel(application) }
    }
}

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DairyRepository = ServiceLocator.repository(application)

    val summary = repository.observeAdminSummary().asUiState(viewModelScope)
    val products = repository.observeProducts().asUiState(viewModelScope)
    val customers = repository.observeCustomers().asUiState(viewModelScope)
    val staff = repository.observeStaff().asUiState(viewModelScope)
    val assignments = repository.observeAssignments().asUiState(viewModelScope)
    val deliveries = repository.observeAdminDeliveries(todayKey()).asUiState(viewModelScope)
    val invoices = repository.observeInvoices().asUiState(viewModelScope)

    fun saveProduct(name: String, unit: String, pricePaise: Long, id: String? = null) {
        viewModelScope.launch { repository.upsertProduct(name, unit, pricePaise, id ?: newId()) }
    }

    fun deleteProduct(id: String) {
        viewModelScope.launch { repository.softDeleteProduct(id) }
    }

    fun saveCustomer(
        name: String,
        phone: String,
        address: String,
        latitude: Double,
        longitude: Double,
        productId: String,
        quantity: Double,
        id: String? = null,
    ) {
        viewModelScope.launch {
            repository.upsertCustomer(name, phone, address, latitude, longitude, productId, quantity, id ?: newId())
        }
    }

    fun deleteCustomer(id: String) {
        viewModelScope.launch { repository.softDeleteCustomer(id) }
    }

    fun saveAssignment(customerId: String, staffId: String, productId: String, quantity: Double, id: String? = null) {
        viewModelScope.launch { repository.upsertAssignment(customerId, staffId, productId, quantity, id ?: newId()) }
    }

    fun deleteAssignment(id: String) {
        viewModelScope.launch { repository.softDeleteAssignment(id) }
    }

    fun markDelivery(id: String, status: DeliveryStatus, reason: String? = null) {
        viewModelScope.launch { repository.markDelivery(id, status, reason) }
    }

    fun markInvoicePaid(invoice: Invoice) {
        viewModelScope.launch { repository.markInvoicePaid(invoice) }
    }

    fun createUser(email: String, password: String, role: UserRole, fullName: String, phone: String) {
        viewModelScope.launch { repository.createUser(email, password, role, fullName, phone) }
    }

    private fun newId() = java.util.UUID.randomUUID().toString()

    companion object {
        fun factory(application: Application) = viewModelFactory { AdminViewModel(application) }
    }
}

class DeliveryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ServiceLocator.repository(application)

    fun todayDeliveries(staffId: String) = repository.observeTodayDeliveries(staffId).asUiState(viewModelScope)

    fun history(staffId: String) = repository.observeHistory(staffId).asUiState(viewModelScope)

    fun earnings(staffId: String) = repository.observeEarnings(staffId).asUiState(viewModelScope)

    fun markDelivery(id: String, status: DeliveryStatus, reason: String? = null) {
        viewModelScope.launch { repository.markDelivery(id, status, reason) }
    }

    companion object {
        fun factory(application: Application) = viewModelFactory { DeliveryViewModel(application) }
    }
}

private fun Throwable.toAuthMessage(): String {
    val text = message.orEmpty()
    return when {
        text.contains("invalid_credentials", ignoreCase = true) -> "Email or password is incorrect. You can create an account or use a demo login."
        text.contains("Supabase request failed", ignoreCase = true) -> "Unable to sign in with Supabase right now. Please check the account details or use demo access."
        else -> text.ifBlank { "Unable to sign in" }
    }
}

private fun <T> kotlinx.coroutines.flow.Flow<T>.asUiState(scope: CoroutineScope): StateFlow<UiState<T>> =
    map<T, UiState<T>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Something went wrong")) }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState.Loading,
        )

private inline fun <VM : ViewModel> viewModelFactory(crossinline create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }

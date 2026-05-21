package com.example.dairyflow2.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dairyflow2.core.UiState
import com.example.dairyflow2.core.asRupees
import com.example.dairyflow2.core.trimQuantity
import com.example.dairyflow2.data.model.Customer
import com.example.dairyflow2.data.model.DeliveryCard
import com.example.dairyflow2.data.model.DeliveryStatus
import com.example.dairyflow2.data.model.Invoice
import com.example.dairyflow2.data.model.Product
import com.example.dairyflow2.data.model.Profile
import com.example.dairyflow2.data.model.UserRole
import com.example.dairyflow2.ui.components.DairyScaffold
import com.example.dairyflow2.ui.components.SectionList
import com.example.dairyflow2.ui.components.SoftCard
import com.example.dairyflow2.ui.components.StateContent
import com.example.dairyflow2.ui.components.StatusChips
import com.example.dairyflow2.ui.components.SummaryCard
import com.example.dairyflow2.ui.viewmodel.AdminViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

private enum class AdminTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Dashboard("Home", Icons.Default.Dashboard),
    Products("Products", Icons.Default.Inventory2),
    Customers("Customers", Icons.Default.Group),
    Staff("Staff", Icons.Default.PersonAdd),
    Assignments("Assign", Icons.Default.AssignmentTurnedIn),
    Deliveries("Delivery", Icons.Default.DeliveryDining),
    Invoices("Invoices", Icons.Default.Payments),
}

@Composable
fun AdminApp(
    viewModel: AdminViewModel,
    syncCount: Int,
    onLogout: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(AdminTab.Dashboard) }
    DairyScaffold(
        title = "Admin dashboard",
        syncCount = syncCount,
        onLogout = onLogout,
        bottomBar = {
            NavigationBar {
                AdminTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                AdminTab.Dashboard -> DashboardScreen(viewModel)
                AdminTab.Products -> ProductsScreen(viewModel)
                AdminTab.Customers -> CustomersScreen(viewModel)
                AdminTab.Staff -> StaffScreen(viewModel)
                AdminTab.Assignments -> AssignmentsScreen(viewModel)
                AdminTab.Deliveries -> DeliveriesScreen(viewModel)
                AdminTab.Invoices -> InvoicesScreen(viewModel)
            }
        }
    }
}

@Composable
private fun DashboardScreen(viewModel: AdminViewModel) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    StateContent(summary) { value ->
        SectionList {
            item {
                Text("Today at a glance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard("Revenue", value.revenuePaise.asRupees(), Icons.Default.AccountBalanceWallet, Modifier.weight(1f))
                    SummaryCard("Products", value.totalProducts.toString(), Icons.Default.Inventory2, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard("Customers", value.activeCustomers.toString(), Icons.Default.Group, Modifier.weight(1f))
                    SummaryCard("Pending", value.pendingDeliveries.toString(), Icons.Default.DeliveryDining, Modifier.weight(1f))
                }
            }
            item {
                SoftCard {
                    Text("Delivery staff", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${value.deliveryStaff} active staff members")
                    StatusChips(listOf("Offline cache enabled", "Realtime-ready assignments", "Monthly invoices"))
                }
            }
        }
    }
}

@Composable
private fun ProductsScreen(viewModel: AdminViewModel) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("L") }
    var price by remember { mutableStateOf("") }

    StateContent(products, empty = { false }) { list ->
        SectionList {
            item {
                SoftCard {
                    Text("Add product", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(price, { price = it }, label = { Text("Price in rupees") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            viewModel.saveProduct(name, unit, ((price.toDoubleOrNull() ?: 0.0) * 100).toLong())
                            name = ""
                            price = ""
                        },
                        enabled = name.isNotBlank() && price.toDoubleOrNull() != null,
                        shape = RoundedCornerShape(8.dp),
                    ) { Text("Save product") }
                }
            }
            items(list, key = { it.id }) { product ->
                ProductRow(product, onDelete = { viewModel.deleteProduct(product.id) })
            }
        }
    }
}

@Composable
private fun ProductRow(product: Product, onDelete: () -> Unit) {
    SoftCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(product.name, fontWeight = FontWeight.SemiBold)
                Text("${product.pricePaise.asRupees()} / ${product.unit}")
            }
            OutlinedButton(onClick = onDelete, enabled = product.isActive) { Text(if (product.isActive) "Deactivate" else "Inactive") }
        }
    }
}

@Composable
private fun CustomersScreen(viewModel: AdminViewModel) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()

    StateContent(customers, empty = { false }) { customerList ->
        val productList = (products as? UiState.Success)?.data.orEmpty()
        CustomerEditor(viewModel, productList, customerList)
    }
}

@Composable
private fun CustomerEditor(viewModel: AdminViewModel, products: List<Product>, customers: List<Customer>) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var productId by remember(products) { mutableStateOf(products.firstOrNull()?.id.orEmpty()) }
    var quantity by remember { mutableStateOf("1") }
    var latitude by remember { mutableDoubleStateOf(28.6139) }
    var longitude by remember { mutableDoubleStateOf(77.2090) }

    SectionList {
        item {
            SoftCard {
                Text("Add customer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(address, { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                ChoiceRow(products, selectedId = productId, label = { it.name }, onSelect = { productId = it.id })
                OutlinedTextField(quantity, { quantity = it }, label = { Text("Daily quantity") }, modifier = Modifier.fillMaxWidth())
                CustomerMap(latitude, longitude) {
                    latitude = it.latitude
                    longitude = it.longitude
                }
                Button(
                    onClick = {
                        viewModel.saveCustomer(name, phone, address, latitude, longitude, productId, quantity.toDoubleOrNull() ?: 1.0)
                        name = ""
                        phone = ""
                        address = ""
                    },
                    enabled = name.isNotBlank() && productId.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Save customer") }
            }
        }
        items(customers, key = { it.id }) { customer ->
            SoftCard {
                Text(customer.name, fontWeight = FontWeight.SemiBold)
                Text(customer.address)
                Text("${customer.quantity.trimQuantity()} daily, ${customer.latitude}, ${customer.longitude}")
                OutlinedButton(onClick = { viewModel.deleteCustomer(customer.id) }, enabled = customer.isActive) {
                    Text(if (customer.isActive) "Deactivate" else "Inactive")
                }
            }
        }
    }
}

@Composable
private fun StaffScreen(viewModel: AdminViewModel) {
    val staff by viewModel.staff.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("password") }
    var role by remember { mutableStateOf(UserRole.DeliveryBoy) }

    StateContent(staff, empty = { false }) { list ->
        SectionList {
            item {
                SoftCard {
                    Text("Create app user", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    RoleSelector(role) { role = it }
                    OutlinedTextField(name, { name = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text("Temporary password") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            viewModel.createUser(email, password, role, name, phone)
                            name = ""
                            email = ""
                            phone = ""
                        },
                        enabled = name.isNotBlank() && email.isNotBlank() && password.length >= 6,
                        shape = RoundedCornerShape(8.dp),
                    ) { Text("Create user") }
                }
            }
            items(list, key = { it.id }) { profile ->
                SoftCard {
                    Text(profile.fullName, fontWeight = FontWeight.SemiBold)
                    Text(profile.email)
                    Text("Rating ${profile.rating} · ${profile.totalDeliveries} deliveries · ${profile.onTimeRate}% on time")
                }
            }
        }
    }
}

@Composable
private fun AssignmentsScreen(viewModel: AdminViewModel) {
    val assignments by viewModel.assignments.collectAsStateWithLifecycle()
    val customers = (viewModel.customers.collectAsStateWithLifecycle().value as? UiState.Success)?.data.orEmpty()
    val products = (viewModel.products.collectAsStateWithLifecycle().value as? UiState.Success)?.data.orEmpty()
    val staff = (viewModel.staff.collectAsStateWithLifecycle().value as? UiState.Success)?.data.orEmpty()
    var customerId by remember(customers) { mutableStateOf(customers.firstOrNull()?.id.orEmpty()) }
    var productId by remember(products) { mutableStateOf(products.firstOrNull()?.id.orEmpty()) }
    var staffId by remember(staff) { mutableStateOf(staff.firstOrNull()?.id.orEmpty()) }
    var quantity by remember { mutableStateOf("1") }

    StateContent(assignments, empty = { false }) { list ->
        SectionList {
            item {
                SoftCard {
                    Text("Assign delivery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    ChoiceRow(customers, customerId, label = { it.name }, onSelect = { customerId = it.id })
                    ChoiceRow(staff, staffId, label = { it.fullName }, onSelect = { staffId = it.id })
                    ChoiceRow(products, productId, label = { it.name }, onSelect = { productId = it.id })
                    OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = { viewModel.saveAssignment(customerId, staffId, productId, quantity.toDoubleOrNull() ?: 1.0) },
                        enabled = customerId.isNotBlank() && staffId.isNotBlank() && productId.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                    ) { Text("Assign and create today delivery") }
                }
            }
            items(list, key = { it.id }) { assignment ->
                val customer = customers.firstOrNull { it.id == assignment.customerId }?.name ?: assignment.customerId
                val staffName = staff.firstOrNull { it.id == assignment.deliveryBoyId }?.fullName ?: assignment.deliveryBoyId
                SoftCard {
                    Text(customer, fontWeight = FontWeight.SemiBold)
                    Text("Assigned to $staffName · ${assignment.quantity.trimQuantity()} units")
                    OutlinedButton(onClick = { viewModel.deleteAssignment(assignment.id) }, enabled = assignment.isActive) {
                        Text(if (assignment.isActive) "Deactivate" else "Inactive")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveriesScreen(viewModel: AdminViewModel) {
    val deliveries by viewModel.deliveries.collectAsStateWithLifecycle()
    StateContent(deliveries, empty = { it.isEmpty() }, emptyMessage = "No deliveries for today") { list ->
        DeliveryList(list, onMark = viewModel::markDelivery)
    }
}

@Composable
private fun InvoicesScreen(viewModel: AdminViewModel) {
    val invoices by viewModel.invoices.collectAsStateWithLifecycle()
    val customers = (viewModel.customers.collectAsStateWithLifecycle().value as? UiState.Success)?.data.orEmpty()
    StateContent(invoices, empty = { it.isEmpty() }, emptyMessage = "No invoices generated yet") { list ->
        SectionList {
            items(list, key = { it.id }) { invoice ->
                val customerName = customers.firstOrNull { it.id == invoice.customerId }?.name ?: invoice.customerId
                InvoiceRow(invoice, customerName, onPaid = { viewModel.markInvoicePaid(invoice) })
            }
        }
    }
}

@Composable
private fun DeliveryList(cards: List<DeliveryCard>, onMark: (String, DeliveryStatus, String?) -> Unit) {
    SectionList {
        items(cards, key = { it.delivery.id }) { card ->
            SoftCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(card.customer.name, fontWeight = FontWeight.SemiBold)
                        Text("${card.product.name} · ${card.customer.quantity.trimQuantity()} ${card.product.unit}")
                        Text(card.customer.address)
                        Text("Staff: ${card.deliveryBoy?.fullName ?: "Unassigned"}")
                    }
                    FilterChip(selected = true, onClick = {}, label = { Text(card.delivery.status.wireName) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onMark(card.delivery.id, DeliveryStatus.Done, null) }, enabled = card.delivery.status == DeliveryStatus.Pending) {
                        Text("Done")
                    }
                    OutlinedButton(onClick = { onMark(card.delivery.id, DeliveryStatus.Skipped, "Marked skipped by admin") }, enabled = card.delivery.status == DeliveryStatus.Pending) {
                        Text("Skip")
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceRow(invoice: Invoice, customerName: String, onPaid: () -> Unit) {
    SoftCard {
        Text(customerName, fontWeight = FontWeight.SemiBold)
        Text("Month ${invoice.month}")
        Text("Subtotal ${invoice.subtotalPaise.asRupees()} · Pending ${invoice.previousPendingPaise.asRupees()}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(invoice.totalPaise.asRupees(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = onPaid, enabled = invoice.status != "paid", shape = RoundedCornerShape(8.dp)) {
                Text(if (invoice.status == "paid") "Paid" else "Mark paid")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleSelector(selected: UserRole, onRole: (UserRole) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        UserRole.entries.forEachIndexed { index, role ->
            SegmentedButton(
                selected = selected == role,
                onClick = { onRole(role) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = UserRole.entries.size),
            ) {
                Text(if (role == UserRole.Admin) "Admin" else "Delivery boy")
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(items: List<T>, selectedId: String, label: (T) -> String, onSelect: (T) -> Unit) where T : Any {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items.take(3).forEach { item ->
            val text = label(item)
            FilterChip(
                selected = item.hashKey() == selectedId || text == selectedId,
                onClick = { onSelect(item) },
                label = { Text(text, maxLines = 1) },
            )
        }
    }
}

private fun Any.hashKey(): String = when (this) {
    is Product -> id
    is Customer -> id
    is Profile -> id
    else -> hashCode().toString()
}

@Composable
private fun CustomerMap(latitude: Double, longitude: Double, onMapClick: (LatLng) -> Unit) {
    val marker = LatLng(latitude, longitude)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(marker, 12f)
    }
    GoogleMap(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        cameraPositionState = cameraPositionState,
        onMapClick = onMapClick,
    ) {
        Marker(state = MarkerState(position = marker), title = "Customer location")
    }
}


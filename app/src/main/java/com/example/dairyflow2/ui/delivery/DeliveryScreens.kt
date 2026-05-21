package com.example.dairyflow2.ui.delivery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dairyflow2.core.asRupees
import com.example.dairyflow2.core.trimQuantity
import com.example.dairyflow2.data.model.DeliveryCard
import com.example.dairyflow2.data.model.DeliveryStatus
import com.example.dairyflow2.data.model.EarningsSummary
import com.example.dairyflow2.data.model.Profile
import com.example.dairyflow2.ui.components.DairyScaffold
import com.example.dairyflow2.ui.components.SectionList
import com.example.dairyflow2.ui.components.SoftCard
import com.example.dairyflow2.ui.components.StateContent
import com.example.dairyflow2.ui.components.StatusChips
import com.example.dairyflow2.ui.components.SummaryCard
import com.example.dairyflow2.ui.viewmodel.DeliveryViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

private enum class DeliveryTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Today("Today", Icons.Default.Map),
    History("History", Icons.Default.History),
    Earnings("Earnings", Icons.Default.Payments),
    Profile("Profile", Icons.Default.AccountCircle),
}

@Composable
fun DeliveryApp(
    profile: Profile,
    viewModel: DeliveryViewModel,
    syncCount: Int,
    onLogout: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(DeliveryTab.Today) }
    DairyScaffold(
        title = "Delivery boy",
        syncCount = syncCount,
        onLogout = onLogout,
        bottomBar = {
            NavigationBar {
                DeliveryTab.entries.forEach { item ->
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
                DeliveryTab.Today -> TodayScreen(profile.id, viewModel)
                DeliveryTab.History -> HistoryScreen(profile.id, viewModel)
                DeliveryTab.Earnings -> EarningsScreen(profile.id, viewModel)
                DeliveryTab.Profile -> ProfileScreen(profile)
            }
        }
    }
}

@Composable
private fun TodayScreen(staffId: String, viewModel: DeliveryViewModel) {
    val todayFlow = remember(staffId) { viewModel.todayDeliveries(staffId) }
    val today by todayFlow.collectAsStateWithLifecycle()

    StateContent(today, empty = { it.isEmpty() }, emptyMessage = "No deliveries assigned for today") { cards ->
        SectionList {
            item {
                RouteMap(cards)
            }
            itemsIndexed(cards, key = { _, item -> item.delivery.id }) { index, card ->
                DeliveryTaskCard(index + 1, card, onMark = viewModel::markDelivery)
            }
        }
    }
}

@Composable
private fun HistoryScreen(staffId: String, viewModel: DeliveryViewModel) {
    val historyFlow = remember(staffId) { viewModel.history(staffId) }
    val history by historyFlow.collectAsStateWithLifecycle()
    StateContent(history, empty = { it.isEmpty() }, emptyMessage = "Completed deliveries will appear here") { cards ->
        SectionList {
            itemsIndexed(cards, key = { _, item -> item.delivery.id }) { _, card ->
                SoftCard {
                    Text(card.customer.name, fontWeight = FontWeight.SemiBold)
                    Text("${card.delivery.deliveryDate} · ${card.product.name} · ${card.delivery.status.wireName}")
                    if (card.delivery.skippedReason != null) Text(card.delivery.skippedReason)
                }
            }
        }
    }
}

@Composable
private fun EarningsScreen(staffId: String, viewModel: DeliveryViewModel) {
    val earningsFlow = remember(staffId) { viewModel.earnings(staffId) }
    val earnings by earningsFlow.collectAsStateWithLifecycle()
    StateContent(earnings) { value ->
        EarningsContent(value)
    }
}

@Composable
private fun EarningsContent(value: EarningsSummary) {
    SectionList {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("Done deliveries", value.completedDeliveries.toString(), Icons.Default.CheckCircle, Modifier.weight(1f))
                SummaryCard("Total earned", value.totalPaise.asRupees(), Icons.Default.Payments, Modifier.weight(1f))
            }
        }
        item {
            SoftCard {
                Text("Rate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${value.ratePerDeliveryPaise.asRupees()} per completed delivery")
                Text("Skipped deliveries are not included in earnings.")
            }
        }
    }
}

@Composable
private fun ProfileScreen(profile: Profile) {
    SectionList {
        item {
            SoftCard {
                Text(profile.fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(profile.email)
                Text(profile.phone)
            }
        }
        item {
            StatusChips(
                listOf(
                    "Rating ${profile.rating}",
                    "${profile.totalDeliveries} deliveries",
                    "${profile.onTimeRate}% on time",
                    "${profile.streak} day streak",
                ),
            )
        }
    }
}

@Composable
private fun DeliveryTaskCard(index: Int, card: DeliveryCard, onMark: (String, DeliveryStatus, String?) -> Unit) {
    SoftCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("$index. ${card.customer.name}", fontWeight = FontWeight.SemiBold)
                Text("${card.product.name} · ${card.customer.quantity.trimQuantity()} ${card.product.unit}")
                Text(card.customer.address)
            }
            FilterChip(selected = true, onClick = {}, label = { Text(card.delivery.status.wireName) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onMark(card.delivery.id, DeliveryStatus.Done, null) },
                enabled = card.delivery.status == DeliveryStatus.Pending,
                shape = RoundedCornerShape(8.dp),
            ) { Text("Done") }
            OutlinedButton(
                onClick = { onMark(card.delivery.id, DeliveryStatus.Skipped, "Customer unavailable") },
                enabled = card.delivery.status == DeliveryStatus.Pending,
            ) { Text("Skip") }
        }
    }
}

@Composable
private fun RouteMap(cards: List<DeliveryCard>) {
    val first = cards.firstOrNull()?.customer
    val center = LatLng(first?.latitude ?: 28.6139, first?.longitude ?: 77.2090)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, 12f)
    }
    GoogleMap(
        modifier = Modifier.fillMaxWidth().height(260.dp),
        cameraPositionState = cameraPositionState,
    ) {
        cards.forEachIndexed { index, card ->
            Marker(
                state = MarkerState(position = LatLng(card.customer.latitude, card.customer.longitude)),
                title = "${index + 1}. ${card.customer.name}",
                snippet = card.customer.address,
            )
        }
    }
}


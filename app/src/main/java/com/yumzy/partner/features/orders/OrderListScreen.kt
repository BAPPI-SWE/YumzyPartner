package com.yumzy.partner.features.orders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

// Data class to represent a complete Order
data class Order(
    val id: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val fullAddress: String = "",
    val userSubLocation: String = "",
    val totalPrice: Double = 0.0,
    val items: List<Map<String, Any>> = emptyList(),
    val createdAt: Timestamp = Timestamp.now()
)

// Data class for the aggregated item counts
data class ItemSummary(
    val name: String,
    val quantity: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderListScreen(
    categoryName: String,
    onBackClicked: () -> Unit,
    onAcceptOrder: (orderId: String) -> Unit,
    onRejectOrder: (orderId: String) -> Unit,
    onAcceptAllOrders: (orderIds: List<String>) -> Unit,
    onRejectAllOrders: (orderIds: List<String>) -> Unit
) {
    var allOrders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var itemSummary by remember { mutableStateOf<List<ItemSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // State for dynamic location filtering
    var locationFilters by remember { mutableStateOf<List<String>>(listOf("All")) }
    var selectedLocation by remember { mutableStateOf("All") }

    val filteredOrders = remember(allOrders, selectedLocation) {
        if (selectedLocation == "All") allOrders else allOrders.filter { it.userSubLocation == selectedLocation }
    }

    // This effect fetches all necessary data: locations and orders
    LaunchedEffect(key1 = categoryName) {
        val restaurantId = Firebase.auth.currentUser?.uid
        if (restaurantId != null) {
            val db = Firebase.firestore
            // First, get the restaurant's serviceable locations to build the filter
            db.collection("restaurants").document(restaurantId).get()
                .addOnSuccessListener { restaurantDoc ->
                    val locations = restaurantDoc.get("deliveryLocations") as? List<String> ?: emptyList()
                    if (locations.isNotEmpty()) {
                        // Now fetch the sub-locations for those base locations
                        db.collection("locations").whereIn("name", locations).get()
                            .addOnSuccessListener { locationDocs ->
                                val subLocs = locationDocs.flatMap { it.get("subLocations") as? List<String> ?: emptyList() }.distinct()
                                locationFilters = listOf("All") + subLocs
                            }
                    }
                }

            // Fetch all pending orders for this category
            db.collection("orders")
                .whereEqualTo("restaurantId", restaurantId)
                .whereEqualTo("preOrderCategory", categoryName)
                .whereEqualTo("orderStatus", "Pending")
                .addSnapshotListener { snapshot, _ ->
                    isLoading = false
                    snapshot?.let {
                        allOrders = it.documents.mapNotNull { doc ->
                            val address = "Building: ${doc.getString("building")}, Floor: ${doc.getString("floor")}, Room: ${doc.getString("room")}\n${doc.getString("userSubLocation")}, ${doc.getString("userBaseLocation")}"
                            doc.toObject(Order::class.java)?.copy(id = doc.id, fullAddress = address)
                        }.sortedBy { it.createdAt }
                    }
                }
        }
    }

    // This effect calculates the item summary whenever the filtered list changes
    LaunchedEffect(filteredOrders) {
        val summaryMap = mutableMapOf<String, Long>()
        filteredOrders.forEach { order ->
            order.items.forEach { item ->
                val name = item["itemName"] as? String ?: "Unknown"
                val quantity = item["quantity"] as? Long ?: 0L
                summaryMap[name] = (summaryMap[name] ?: 0L) + quantity
            }
        }
        itemSummary = summaryMap.map { ItemSummary(it.key, it.value) }.sortedByDescending { it.quantity }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Orders for ${categoryName.removePrefix("Pre-order ")}") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Item Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (itemSummary.isEmpty() && !isLoading) Text("No pending orders found.")
                    else {
                        itemSummary.forEach {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(it.name, fontWeight = FontWeight.SemiBold)
                                Text("${it.quantity} orders")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterDropdown(
                    selectedLocation = selectedLocation,
                    options = locationFilters,
                    onSelectionChanged = { selectedLocation = it },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)){
                Button(
                    onClick = { onAcceptAllOrders(filteredOrders.map { it.id }) },
                    enabled = filteredOrders.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("Accept All") }
                OutlinedButton(
                    onClick = { onRejectAllOrders(filteredOrders.map { it.id }) },
                    enabled = filteredOrders.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                ) { Text("Reject All") }
            }
            Spacer(Modifier.height(8.dp))
            Text("Filtered Orders (${filteredOrders.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (filteredOrders.isEmpty()){
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No orders match the current filter.") }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filteredOrders) { order ->
                        OrderCard(
                            order = order,
                            onAccept = { onAcceptOrder(order.id) },
                            onReject = { onRejectOrder(order.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDropdown(selectedLocation: String, options: List<String>, onSelectionChanged: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
        OutlinedTextField(
            value = "Filter by: $selectedLocation",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    onSelectionChanged(option)
                    expanded = false
                })
            }
        }
    }
}

@Composable
fun OrderCard(order: Order, onAccept: () -> Unit, onReject: () -> Unit) {
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween){
                Text(order.userName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(sdf.format(order.createdAt.toDate()), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Text(order.fullAddress, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Text("Contact: ${order.userPhone}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Divider(Modifier.padding(vertical = 8.dp))
            order.items.forEach { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${item["quantity"]} x ${item["itemName"]}")
                }
            }
            Divider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Total: ৳${order.totalPrice}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)){
                    OutlinedButton(onClick = onReject, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) { Text("Reject") }
                    Button(onClick = onAccept) { Text("Accept") }
                }
            }
        }
    }
}
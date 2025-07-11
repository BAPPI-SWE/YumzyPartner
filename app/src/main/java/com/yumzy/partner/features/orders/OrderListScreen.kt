package com.yumzy.partner.features.orders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

// Data class to represent an Order
data class Order(
    val id: String = "",
    val userName: String = "",
    val userBaseLocation: String = "",
    val userSubLocation: String = "",
    val totalPrice: Double = 0.0,
    val orderStatus: String = "",
    val items: List<Map<String, Any>> = emptyList()
)

// Data class for the aggregated item counts
data class ItemSummary(
    val name: String,
    val quantity: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderListScreen(categoryName: String) {
    var allOrders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var filteredOrders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var itemSummary by remember { mutableStateOf<List<ItemSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // State for filtering
    val locationFilters = listOf("All", "Hall 1", "Hall 2", "Faculty Room A")
    var selectedLocation by remember { mutableStateOf("All") }

    // Fetch orders
    LaunchedEffect(key1 = categoryName) {
        val restaurantId = Firebase.auth.currentUser?.uid
        if (restaurantId != null) {
            Firebase.firestore.collection("orders")
                .whereEqualTo("restaurantId", restaurantId)
                .whereEqualTo("preOrderCategory", categoryName)
                .addSnapshotListener { snapshot, _ ->
                    isLoading = false
                    snapshot?.let {
                        val orders = it.documents.mapNotNull { doc ->
                            Order(
                                id = doc.id,
                                userName = doc.getString("userName") ?: "",
                                userBaseLocation = doc.getString("userBaseLocation") ?: "",
                                userSubLocation = doc.getString("userSubLocation") ?: "",
                                totalPrice = doc.getDouble("totalPrice") ?: 0.0,
                                orderStatus = doc.getString("orderStatus") ?: "",
                                items = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                            )
                        }
                        allOrders = orders
                    }
                }
        }
    }

    // Apply filter and calculate summary whenever the selection or the order list changes
    LaunchedEffect(allOrders, selectedLocation) {
        filteredOrders = if (selectedLocation == "All") {
            allOrders
        } else {
            allOrders.filter { it.userSubLocation == selectedLocation }
        }

        // Calculate summary based on the filtered orders
        val summaryMap = mutableMapOf<String, Long>()
        filteredOrders.forEach { order ->
            order.items.forEach { item ->
                val name = item["itemName"] as? String ?: "Unknown"
                val quantity = item["quantity"] as? Long ?: 0L
                summaryMap[name] = (summaryMap[name] ?: 0L) + quantity
            }
        }
        itemSummary = summaryMap.map { ItemSummary(it.key, it.value) }
    }


    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Orders for ${categoryName.removePrefix("Pre-order ")}") })
        }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            // Summary Section
            Text("Item Summary", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Card {
                Column(Modifier.padding(16.dp)) {
                    itemSummary.forEach {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(it.name, fontWeight = FontWeight.SemiBold)
                            Text("${it.quantity} orders")
                        }
                    }
                    if (itemSummary.isEmpty()) Text("No orders found for this filter.")
                }
            }

            Spacer(Modifier.height(24.dp))

            // Filter Dropdown
            FilterDropdown(
                selectedLocation = selectedLocation,
                options = locationFilters,
                onSelectionChanged = { selectedLocation = it }
            )
            Spacer(Modifier.height(16.dp))

            // Order List
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filteredOrders) { order ->
                        OrderCard(order = order)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDropdown(selectedLocation: String, options: List<String>, onSelectionChanged: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
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
fun OrderCard(order: Order) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(order.userName, style = MaterialTheme.typography.titleMedium)
            Text("Location: ${order.userSubLocation}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Divider(Modifier.padding(vertical = 8.dp))
            order.items.forEach { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${item["quantity"]} x ${item["itemName"]}")
                    Text("৳${item["price"]}")
                }
            }
            Divider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("Total: ৳${order.totalPrice}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}
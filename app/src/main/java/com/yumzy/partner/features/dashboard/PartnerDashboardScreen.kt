package com.yumzy.partner.features.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

// --- DATA CLASSES (FROM YOUR FILE) ---
data class PreOrderCategory(
    val id: String,
    val name: String,
    val startTime: String,
    val endTime: String,
    val deliveryTime: String
)

data class MenuItem(
    val id: String,
    val name: String,
    val price: Double,
    val category: String
)

// NEW: Data class to hold basic order info
data class OrderInfo(
    val preOrderCategory: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerDashboardScreen(
    onNavigateToCreateCategory: () -> Unit,
    onNavigateToAddItem: (category: String) -> Unit,
    onNavigateToCategoryDetail: (categoryName: String) -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onDeleteItem: (itemId: String) -> Unit,
    onDeleteCategory: (category: PreOrderCategory) -> Unit
) {
    var preOrderCategories by remember { mutableStateOf<List<PreOrderCategory>>(emptyList()) }
    var allMenuItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // NEW: State to hold all incoming pre-orders
    var incomingPreOrders by remember { mutableStateOf<List<OrderInfo>>(emptyList()) }

    LaunchedEffect(key1 = Unit) {
        val ownerId = Firebase.auth.currentUser?.uid
        if (ownerId != null) {
            val db = Firebase.firestore
            db.collection("restaurants").document(ownerId).collection("preOrderCategories")
                .addSnapshotListener { snapshot, _ ->
                    snapshot?.let {
                        preOrderCategories = it.documents.mapNotNull { doc ->
                            PreOrderCategory(
                                id = doc.id,
                                name = doc.getString("name") ?: "",
                                startTime = doc.getString("startTime") ?: "",
                                endTime = doc.getString("endTime") ?: "",
                                deliveryTime = doc.getString("deliveryTime") ?: ""
                            )
                        }
                    }
                    isLoading = false
                }
            db.collection("restaurants").document(ownerId).collection("menuItems")
                .addSnapshotListener { snapshot, _ ->
                    snapshot?.let {
                        allMenuItems = it.documents.mapNotNull { doc ->
                            MenuItem(
                                id = doc.id,
                                name = doc.getString("name") ?: "",
                                price = doc.getDouble("price") ?: 0.0,
                                category = doc.getString("category") ?: ""
                            )
                        }
                    }
                }

            // NEW: Listener for incoming pre-orders
            db.collection("orders")
                .whereEqualTo("restaurantId", ownerId)
                .whereEqualTo("orderType", "PreOrder")
                .whereEqualTo("orderStatus", "Pending")
                .addSnapshotListener { snapshot, _ ->
                    snapshot?.let {
                        incomingPreOrders = it.documents.mapNotNull { doc ->
                            doc.toObject(OrderInfo::class.java)
                        }
                    }
                }
        }
    }

    val currentMenuItems = allMenuItems.filter { it.category == "Current Menu" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Menu Management") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = onNavigateToEditProfile) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Text("Pre-Order", fontSize = 22.sp, fontWeight = FontWeight.Bold) }

                items(preOrderCategories) { category ->
                    // Calculate the order count for this specific category
                    val orderCount = incomingPreOrders.count { it.preOrderCategory == "Pre-order ${category.name}" }
                    PreOrderCategoryCard(
                        category = category,
                        orderCount = orderCount, // Pass the count to the card
                        onClick = { onNavigateToCategoryDetail("Pre-order ${category.name}") },
                        onDelete = { onDeleteCategory(category) }
                    )
                }

                item {
                    OutlinedButton(onClick = onNavigateToCreateCategory, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add Pre-Order Category")
                    }
                }

                item { Divider(modifier = Modifier.padding(vertical = 16.dp)) }

                item { Text("Current Menu", fontSize = 22.sp, fontWeight = FontWeight.Bold) }

                if (currentMenuItems.isEmpty()) {
                    item {
                        Text(text = "No items in the current menu.", color = Color.Gray, modifier = Modifier.padding(8.dp))
                    }
                } else {
                    items(currentMenuItems) { item ->
                        CurrentMenuItemCard(item = item, onDelete = { onDeleteItem(item.id) })
                    }
                }

                item {
                    OutlinedButton(onClick = { onNavigateToAddItem("Current Menu") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add more item")
                    }
                }
            }
        }
    }
}

// UPDATED: PreOrderCategoryCard now accepts an orderCount
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreOrderCategoryCard(category: PreOrderCategory, orderCount: Int, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = category.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(text = "Order: ${category.startTime} - ${category.endTime}", color = Color.Gray, fontSize = 14.sp)
                Text(text = "Delivery: ${category.deliveryTime}", color = Color.Gray, fontSize = 14.sp)
            }

            // NEW: The order badge and button
            BadgedBox(
                badge = {
                    if(orderCount > 0) {
                        Badge { Text("$orderCount") }
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.ReceiptLong, contentDescription = "View Orders")
            }

            Icon(Icons.Default.ChevronRight, contentDescription = "View Category")
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Category", tint = Color.Gray)
            }
        }
    }
}

@Composable
fun CurrentMenuItemCard(item: MenuItem, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f).padding(vertical = 16.dp)
            )
            Text(text = "৳${item.price}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Item", tint = Color.Gray)
            }
        }
    }
}
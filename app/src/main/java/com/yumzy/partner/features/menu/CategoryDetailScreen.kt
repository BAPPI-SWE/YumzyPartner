package com.yumzy.partner.features.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.partner.features.dashboard.MenuItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    categoryName: String,
    onNavigateToAddItem: (category: String) -> Unit,
    onNavigateToOrderList: (category: String) -> Unit,
    onDeleteItem: (itemId: String) -> Unit
) {
    var menuItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var orderCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(key1 = categoryName) {
        val ownerId = Firebase.auth.currentUser?.uid
        if (ownerId != null) {
            val db = Firebase.firestore

            db.collection("restaurants").document(ownerId).collection("menuItems")
                .whereEqualTo("category", categoryName)
                .addSnapshotListener { snapshot, _ ->
                    isLoading = false
                    snapshot?.let {
                        menuItems = it.documents.mapNotNull { doc ->
                            MenuItem(
                                id = doc.id,
                                name = doc.getString("name") ?: "No Name",
                                price = doc.getDouble("price") ?: 0.0,
                                category = doc.getString("category") ?: "No Category"
                            )
                        }
                    }
                }

            db.collection("orders")
                .whereEqualTo("restaurantId", ownerId)
                .whereEqualTo("preOrderCategory", categoryName)
                .addSnapshotListener { snapshot, _ ->
                    snapshot?.let { orderCount = it.size() }
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(categoryName.removePrefix("Pre-order ").trim()) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { onNavigateToOrderList(categoryName) }) {
                        BadgedBox(
                            badge = {
                                if (orderCount > 0) {
                                    Badge { Text("$orderCount") }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.ReceiptLong,
                                contentDescription = "View Orders",
                                tint = Color.White
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToAddItem(categoryName) }) {
                Icon(Icons.Default.Add, contentDescription = "Add Item to Category")
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (menuItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No items in this category yet.\nClick '+' to add one.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(menuItems) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 16.dp)
                            )
                            Text(text = "৳${item.price}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            IconButton(onClick = { onDeleteItem(item.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Item", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}
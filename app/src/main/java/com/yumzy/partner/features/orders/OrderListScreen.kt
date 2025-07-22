package com.yumzy.partner.features.orders

import android.content.Context
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

// Your data classes are correct
data class Order(
    val id: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val userBaseLocation: String = "",
    val userSubLocation: String = "",
    val fullAddress: String = "",
    val totalPrice: Double = 0.0,
    val orderStatus: String = "",
    val items: List<Map<String, Any>> = emptyList(),
    val createdAt: Timestamp = Timestamp.now()
)

data class ItemSummary(val name: String, val quantity: Long)

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
    var locationFilters by remember { mutableStateOf<List<String>>(listOf("All")) }
    var selectedLocation by remember { mutableStateOf("All") }
    var restaurantName by remember { mutableStateOf("") }

    // NEW: State for the custom notification
    var customMessage by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    val filteredOrders = remember(allOrders, selectedLocation) {
        if (selectedLocation == "All") allOrders else allOrders.filter { it.userSubLocation == selectedLocation }
    }
    val context = LocalContext.current

    LaunchedEffect(key1 = categoryName) {
        val restaurantId = Firebase.auth.currentUser?.uid
        if (restaurantId != null) {
            val db = Firebase.firestore
            db.collection("restaurants").document(restaurantId).get()
                .addOnSuccessListener { restaurantDoc ->
                    restaurantName = restaurantDoc.getString("name") ?: "Your Restaurant"
                    val locations = restaurantDoc.get("deliveryLocations") as? List<String> ?: emptyList()
                    if (locations.isNotEmpty()) {
                        db.collection("locations").whereIn("name", locations).get()
                            .addOnSuccessListener { locationDocs ->
                                val subLocs = locationDocs.flatMap { it.get("subLocations") as? List<String> ?: emptyList() }.distinct()
                                locationFilters = listOf("All") + subLocs
                            }
                    }
                }

            db.collection("orders")
                .whereEqualTo("restaurantId", restaurantId)
                .whereEqualTo("preOrderCategory", categoryName)
                .addSnapshotListener { snapshot, _ ->
                    isLoading = false
                    snapshot?.let {
                        val orders = it.documents.mapNotNull { doc ->
                            val address = "Building: ${doc.getString("building")}, Floor: ${doc.getString("floor")}, Room: ${doc.getString("room")}\n${doc.getString("userSubLocation")}, ${doc.getString("userBaseLocation")}"
                            doc.toObject(Order::class.java)?.copy(id = doc.id, fullAddress = address)
                        }
                        allOrders = orders
                    }
                }
        }
    }

    LaunchedEffect(filteredOrders) {
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

    fun sendNotification() {
        if (customMessage.isBlank()) {
            Toast.makeText(context, "Please enter a message", Toast.LENGTH_SHORT).show()
            return
        }
        if (filteredOrders.isEmpty()) {
            Toast.makeText(context, "No users to notify for this filter", Toast.LENGTH_SHORT).show()
            return
        }
        isSending = true
        val functions = Firebase.functions
        val orderIds = filteredOrders.map { it.id }

        val data = hashMapOf(
            "orderIds" to orderIds,
            "message" to customMessage,
            "restaurantName" to restaurantName
        )

        functions.getHttpsCallable("sendNotificationToFilteredUsers")
            .call(data)
            .addOnCompleteListener { task ->
                isSending = false
                if (task.isSuccessful) {
                    Toast.makeText(context, "Notifications sent!", Toast.LENGTH_SHORT).show()
                    customMessage = ""
                } else {
                    Toast.makeText(context, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Orders for ${categoryName.removePrefix("Pre-order ")}") },
                navigationIcon = { IconButton(onClick = onBackClicked) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(
                        onClick = {
                            if (filteredOrders.isNotEmpty()) {
                                val printableContent = formatOrdersToHtml(filteredOrders, itemSummary, categoryName, selectedLocation)
                                printOrders(context, printableContent)
                            }
                        },
                        enabled = filteredOrders.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Print Orders")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
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
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterDropdown(selectedLocation = selectedLocation, options = locationFilters, onSelectionChanged = { selectedLocation = it }, modifier = Modifier.weight(1f))
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAcceptAllOrders(filteredOrders.map { it.id }) }, enabled = filteredOrders.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Accept All") }
                    OutlinedButton(onClick = { onRejectAllOrders(filteredOrders.map { it.id }) }, enabled = filteredOrders.isNotEmpty(), modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) { Text("Reject All") }
                }
            }

            // NEW: Card for sending notifications
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Send Custom Notification", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Send a message to all users in the filtered list below.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = customMessage, onValueChange = { customMessage = it }, label = { Text("Your message...") }, placeholder = { Text("e.g., Your food is ready!") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { sendNotification() },
                            enabled = filteredOrders.isNotEmpty() && !isSending,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Send, contentDescription = "Send")
                                Spacer(Modifier.width(8.dp))
                                Text("Send to ${filteredOrders.size} users")
                            }
                        }
                    }
                }
            }

            item {
                Text("Filtered Orders (${filteredOrders.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (isLoading) {
                item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if (filteredOrders.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No orders match the current filter.") } }
            } else {
                items(filteredOrders) { order ->
                    OrderCard(order = order, onAccept = { onAcceptOrder(order.id) }, onReject = { onRejectOrder(order.id) })
                }
            }
        }
    }
}
// ... (FilterDropdown, OrderCard, and Print functions are unchanged)

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

// This function takes the order data and formats it into a professional HTML string
private fun formatOrdersToHtml(orders: List<Order>, summary: List<ItemSummary>, category: String, location: String): String {
    val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
    val date = sdf.format(Date())
    val locationFilter = if (location == "All") "All Locations" else location
    val categoryName = category.removePrefix("Pre-order ")

    val builder = StringBuilder()
    builder.append("""
        <html>
        <head>
            <style>
                body { font-family: sans-serif; margin: 20px; }
                .header { text-align: center; border-bottom: 2px solid #333; padding-bottom: 10px; margin-bottom: 20px; }
                h1 { margin: 0; }
                h2, h3, h4 { margin-top: 20px; margin-bottom: 10px; }

                /* summary table styling (unchanged) */
                table { width: 100%; border-collapse: collapse; }
                th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                th { background-color: #f2f2f2; }

                /* container for all the little tickets */
                .orders-container {
                  display: flex;
                  flex-wrap: wrap;
                  justify-content: space-between;
                  margin-top: 20px;
                }

                /* each ticket card */
                .order-card {
                  box-sizing: border-box;
                  width: 25%;           /* three across */
                  margin-bottom: 20px;  /* space between rows */
                  border: 1px solid #ccc;
                  border-radius: 8px;
                  padding: 10px;
                  page-break-inside: avoid;
                }

                .order-card .info {
                  margin-bottom: 10px;
                  line-height: 1.4;
                }

                .order-card .items {
                  margin-bottom: 10px;
                }

                .order-card .items ul {
                  padding-left: 20px;
                  margin: 0;
                }

                .order-card .items li {
                  margin-bottom: 4px;
                }

                .order-card .total {
                  font-weight: bold;
                  text-align: right;
                }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>Order Production Sheet</h1>
                <h2>Category: $categoryName</h2>
                <p>Date: $date | Location Filter: $locationFilter</p>
            </div>

            <h3>Total Items to Prepare</h3>
            <table>
                <tr><th>Item Name</th><th>Total Quantity</th></tr>
    """.trimIndent())

    summary.forEach {
        builder.append("<tr><td>${it.name}</td><td>${it.quantity}</td></tr>")
    }

    builder.append("""
            </table>
            <hr>
            <h2>Individual Orders (${orders.size})</h2>

            <div class="orders-container">
    """.trimIndent())

    orders.forEach { order ->
        builder.append("""
            <div class="order-card">
              <!-- top info block -->
              <div class="info">
                Name: ${order.userName}<br/>
                Contact: ${order.userPhone}<br/>
                Address: ${order.fullAddress.replace("\n","<br/>")}
              </div>

              <!-- items list -->
              <div class="items">
                <strong>Items:</strong>
                <ul>
        """.trimIndent())

        order.items.forEach { item ->
            builder.append("<li>${item["itemName"]} x ${item["quantity"]}</li>")
        }

        builder.append("""
                </ul>
              </div>

              <!-- total -->
              <div class="total">Total: ৳${order.totalPrice}</div>
            </div>
        """.trimIndent())
    }

    builder.append("""
            </div> <!-- .orders-container -->
        </body>
        </html>
    """.trimIndent())

    return builder.toString()
}


// This function uses the Android system's PrintManager to create the print job
private fun printOrders(context: Context, htmlContent: String) {
    val webView = WebView(context).apply {
        loadDataWithBaseURL(null, htmlContent, "text/HTML", "UTF-8", null)
    }

    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val jobName = "Yumzy_Orders_${System.currentTimeMillis()}"

    val printAdapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        webView.createPrintDocumentAdapter(jobName)
    } else {
        @Suppress("DEPRECATION")
        webView.createPrintDocumentAdapter()
    }

    printManager.print(
        jobName,
        printAdapter,
        PrintAttributes.Builder().build()
    )
}
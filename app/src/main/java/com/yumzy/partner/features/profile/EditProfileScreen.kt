package com.yumzy.partner.features.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onSaveChanges: (
        restaurantName: String,
        cuisine: String, // Add cuisine here
        imageUrl: String,
        servesDaffodil: Boolean,
        servesNsu: Boolean
    ) -> Unit
) {
    // State variables to hold the form data
    var restaurantName by remember { mutableStateOf("") }
    var cuisine by remember { mutableStateOf("") } // Add state for cuisine
    var imageUrl by remember { mutableStateOf("") }
    var servesDaffodil by remember { mutableStateOf(false) }
    var servesNsu by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    // This effect fetches the current restaurant data once
    LaunchedEffect(key1 = Unit) {
        val ownerId = Firebase.auth.currentUser?.uid
        if (ownerId != null) {
            Firebase.firestore.collection("restaurants").document(ownerId).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        restaurantName = document.getString("name") ?: ""
                        cuisine = document.getString("cuisine") ?: "" // Fetch cuisine
                        imageUrl = document.getString("imageUrl") ?: ""
                        val locations = document.get("deliveryLocations") as? List<String> ?: emptyList()
                        servesDaffodil = locations.contains("Daffodil Smart City")
                        servesNsu = locations.contains("North South University")
                    }
                    isLoading = false
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Restaurant Profile") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = restaurantName,
                    onValueChange = { restaurantName = it },
                    label = { Text("Restaurant Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))

                // Add the cuisine text field
                OutlinedTextField(
                    value = cuisine,
                    onValueChange = { cuisine = it },
                    label = { Text("Cuisine (e.g., Pizza, Burger)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("Restaurant Image URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(24.dp))

                Text("Delivery Locations", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = servesDaffodil, onCheckedChange = { servesDaffodil = it })
                    Text("Daffodil Smart City")
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = servesNsu, onCheckedChange = { servesNsu = it })
                    Text("North South University")
                }
                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = { onSaveChanges(restaurantName, cuisine, imageUrl, servesDaffodil, servesNsu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Save Changes", fontSize = 16.sp)
                }
            }
        }
    }
}
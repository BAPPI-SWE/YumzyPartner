package com.yumzy.partner.features.profile

import androidx.compose.foundation.clickable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.partner.features.location.LocationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onSaveChanges: (restaurantName: String, cuisine: String, imageUrl: String, deliveryLocations: List<String>) -> Unit,
    locationViewModel: LocationViewModel = viewModel()
) {
    var restaurantName by remember { mutableStateOf("") }
    var cuisine by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    val locationState by locationViewModel.uiState.collectAsState()

    LaunchedEffect(key1 = Unit) {
        val ownerId = Firebase.auth.currentUser?.uid
        if (ownerId != null) {
            Firebase.firestore.collection("restaurants").document(ownerId).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        restaurantName = document.getString("name") ?: ""
                        cuisine = document.getString("cuisine") ?: ""
                        imageUrl = document.getString("imageUrl") ?: ""
                        val savedLocations = document.get("deliveryLocations") as? List<String> ?: emptyList()
                        // Wait for locations to be fetched before setting selections
                        if (locationState.allLocations.isNotEmpty()) {
                            locationViewModel.setInitialSelections(savedLocations)
                        }
                    }
                    isLoading = false
                }
        }
    }

    // This effect runs when locations are loaded to set the initial state
    LaunchedEffect(locationState.allLocations) {
        if (locationState.allLocations.isNotEmpty() && !isLoading) {
            val ownerId = Firebase.auth.currentUser?.uid ?: return@LaunchedEffect
            Firebase.firestore.collection("restaurants").document(ownerId).get().addOnSuccessListener { document ->
                val savedLocations = document?.get("deliveryLocations") as? List<String> ?: emptyList()
                locationViewModel.setInitialSelections(savedLocations)
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
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = restaurantName, onValueChange = { restaurantName = it }, label = { Text("Restaurant Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = cuisine, onValueChange = { cuisine = it }, label = { Text("Cuisine (e.g., Pizza, Burger)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = imageUrl, onValueChange = { imageUrl = it }, label = { Text("Restaurant Image URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(24.dp))

                Text("Delivery Locations", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))

                locationState.baseLocationOptions.forEach { baseLocation ->
                    val subLocations = locationState.allLocations[baseLocation] ?: emptyList()
                    if (subLocations.isNotEmpty()) {
                        Text(baseLocation, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        subLocations.forEach { subLocation ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { locationViewModel.onSubLocationToggled(baseLocation, subLocation) }.padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = locationState.selectedSubLocations[baseLocation]?.contains(subLocation) ?: false,
                                    onCheckedChange = { locationViewModel.onSubLocationToggled(baseLocation, subLocation) }
                                )
                                Text(subLocation)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = { onSaveChanges(restaurantName, cuisine, imageUrl, locationViewModel.getFinalDeliveryLocations()) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Save Changes", fontSize = 16.sp)
                }
            }
        }
    }
}
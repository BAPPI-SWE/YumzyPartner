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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantProfileScreen(
    onSaveClicked: (
        restaurantName: String,
        cuisine: String,
        servesDaffodil: Boolean,
        servesNsu: Boolean
    ) -> Unit
) {
    var restaurantName by remember { mutableStateOf("") }
    var cuisine by remember { mutableStateOf("") }
    var servesDaffodil by remember { mutableStateOf(true) }
    var servesNsu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Your Restaurant") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Button(
                onClick = { onSaveClicked(restaurantName, cuisine, servesDaffodil, servesNsu) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Save & Continue", modifier = Modifier.padding(8.dp))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Text("Restaurant Details", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = restaurantName,
                onValueChange = { restaurantName = it },
                label = { Text("Restaurant Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = cuisine,
                onValueChange = { cuisine = it },
                label = { Text("Cuisine (e.g., Pizza, Burger, Kebab)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(24.dp))

            Text("Delivery Locations", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text("Select where you can deliver.", fontSize = 14.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(checked = servesDaffodil, onCheckedChange = { servesDaffodil = it })
                Text("Daffodil Smart City")
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(checked = servesNsu, onCheckedChange = { servesNsu = it })
                Text("North South University")
            }
        }
    }
}
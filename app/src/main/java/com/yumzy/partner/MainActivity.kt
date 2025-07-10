package com.yumzy.partner

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.identity.Identity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.partner.auth.AuthScreen
import com.yumzy.partner.auth.AuthViewModel
import com.yumzy.partner.auth.GoogleAuthUiClient
import com.yumzy.partner.features.dashboard.PartnerDashboardScreen
import com.yumzy.partner.features.menu.AddMenuItemScreen // Import new screen
import com.yumzy.partner.features.profile.RestaurantProfileScreen
import com.yumzy.partner.ui.theme.YumzyPartnerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val googleAuthUiClient by lazy {
        GoogleAuthUiClient(
            context = applicationContext,
            oneTapClient = Identity.getSignInClient(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YumzyPartnerTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "auth") {
                    // ... "auth" and "create_profile" composables remain the same

                    composable("auth") {
                        val viewModel = viewModel<AuthViewModel>()
                        val state by viewModel.state.collectAsStateWithLifecycle()

                        LaunchedEffect(key1 = Unit) {
                            val currentUser = googleAuthUiClient.getSignedInUser()
                            if (currentUser != null) {
                                checkRestaurantProfile(currentUser.userId, navController)
                            }
                        }

                        val launcher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartIntentSenderForResult()
                        ) { result ->
                            if (result.resultCode == RESULT_OK) {
                                lifecycleScope.launch {
                                    val signInResult = googleAuthUiClient.signInWithIntent(
                                        intent = result.data ?: return@launch
                                    )
                                    viewModel.onSignInResult(signInResult)
                                }
                            }
                        }

                        LaunchedEffect(key1 = state.isSignInSuccessful) {
                            if (state.isSignInSuccessful) {
                                val userId = googleAuthUiClient.getSignedInUser()?.userId
                                if (userId != null) {
                                    checkRestaurantProfile(userId, navController)
                                }
                                viewModel.resetState()
                            }
                        }

                        AuthScreen(onSignInSuccess = {
                            lifecycleScope.launch {
                                val signInIntentSender = googleAuthUiClient.signIn()
                                launcher.launch(
                                    IntentSenderRequest.Builder(
                                        signInIntentSender ?: return@launch
                                    ).build()
                                )
                            }
                        })
                    }

                    composable("create_profile") {
                        val userId = Firebase.auth.currentUser?.uid
                        if (userId == null) {
                            navController.navigate("auth") { popUpTo("auth") { inclusive = true } }
                            return@composable
                        }
                        RestaurantProfileScreen(onSaveClicked = { name, cuisine, servesDaffodil, servesNsu ->
                            val deliveryLocations = mutableListOf<String>()
                            if(servesDaffodil) deliveryLocations.add("Daffodil Smart City")
                            if(servesNsu) deliveryLocations.add("North South University")

                            val restaurantProfile = hashMapOf(
                                "ownerId" to userId,
                                "name" to name,
                                "cuisine" to cuisine,
                                "deliveryLocations" to deliveryLocations,
                                "email" to (Firebase.auth.currentUser?.email ?: "")
                            )

                            Firebase.firestore.collection("restaurants").document(userId)
                                .set(restaurantProfile)
                                .addOnSuccessListener {
                                    navController.navigate("dashboard") { popUpTo("auth") { inclusive = true } }
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(applicationContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                        })
                    }

                    composable("dashboard") {
                        PartnerDashboardScreen(
                            onNavigateToAddItem = {
                                navController.navigate("add_item")
                            }
                        )
                    }

                    // NEW: Add the route and logic for the AddMenuItemScreen
                    composable("add_item") {
                        val ownerId = Firebase.auth.currentUser?.uid
                        if(ownerId == null) {
                            navController.navigate("auth") { popUpTo("auth") { inclusive = true } }
                            return@composable
                        }

                        AddMenuItemScreen(
                            onSaveItemClicked = { itemName, price, category ->
                                val newItem = hashMapOf(
                                    "name" to itemName,
                                    "price" to price.toDoubleOrNull(), // Convert price to number
                                    "category" to category,
                                    "isAvailable" to true // Default availability
                                )

                                // We save menu items in a "menuItems" subcollection inside the restaurant's document
                                Firebase.firestore.collection("restaurants").document(ownerId)
                                    .collection("menuItems")
                                    .add(newItem) // .add() creates a new document with a random ID
                                    .addOnSuccessListener {
                                        Toast.makeText(applicationContext, "$itemName added!", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack() // Go back to the dashboard
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(applicationContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkRestaurantProfile(ownerId: String, navController: NavController) {
        // This function remains the same
        val db = Firebase.firestore
        db.collection("restaurants").document(ownerId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    navController.navigate("dashboard") { popUpTo("auth") { inclusive = true } }
                } else {
                    navController.navigate("create_profile") { popUpTo("auth") { inclusive = true } }
                }
            }
            .addOnFailureListener {
                Toast.makeText(applicationContext, "Error checking profile.", Toast.LENGTH_LONG).show()
            }
    }
}
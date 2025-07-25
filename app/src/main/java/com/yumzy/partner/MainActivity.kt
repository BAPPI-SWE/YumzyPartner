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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.gms.auth.api.identity.Identity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.partner.auth.AuthScreen
import com.yumzy.partner.auth.AuthViewModel
import com.yumzy.partner.auth.GoogleAuthUiClient
import com.yumzy.partner.features.dashboard.PartnerDashboardScreen
import com.yumzy.partner.features.menu.AddMenuItemScreen
import com.yumzy.partner.features.menu.CategoryDetailScreen
import com.yumzy.partner.features.menu.CreateCategoryScreen
import com.yumzy.partner.features.orders.OrderListScreen
import com.yumzy.partner.features.profile.EditProfileScreen
import com.yumzy.partner.features.profile.RestaurantProfileScreen
import com.yumzy.partner.ui.theme.YumzyPartnerTheme
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
                        val userId = Firebase.auth.currentUser?.uid ?: return@composable
                        RestaurantProfileScreen(onSaveClicked = { name, cuisine, deliveryLocations ->
                            val restaurantProfile = hashMapOf(
                                "ownerId" to userId, "name" to name, "cuisine" to cuisine,
                                "deliveryLocations" to deliveryLocations, // Updated
                                "email" to (Firebase.auth.currentUser?.email ?: "")
                            )

                            Firebase.firestore.collection("restaurants").document(userId)
                                .set(restaurantProfile)
                                .addOnSuccessListener {
                                    navController.navigate("dashboard") { popUpTo("auth") { inclusive = true } }
                                }
                        })
                    }

                    composable("dashboard") {
                        val ownerId = Firebase.auth.currentUser?.uid ?: return@composable
                        PartnerDashboardScreen(
                            onNavigateToCreateCategory = { navController.navigate("create_category") },
                            onNavigateToAddItem = { category ->
                                val encodedCategory = URLEncoder.encode(category, StandardCharsets.UTF_8.toString())
                                navController.navigate("add_item/$encodedCategory")
                            },
                            onNavigateToCategoryDetail = { categoryName ->
                                val encodedCategoryName = URLEncoder.encode(categoryName, StandardCharsets.UTF_8.toString())
                                navController.navigate("category_detail/$encodedCategoryName")
                            },
                            onNavigateToEditProfile = {
                                navController.navigate("edit_profile")
                            },
                            onDeleteItem = { itemId ->
                                deleteMenuItem(ownerId, itemId)
                            },
                            onDeleteCategory = { category ->
                                deleteCategory(ownerId, category.id, "Pre-order ${category.name}")
                            }
                        )
                    }

                    composable("edit_profile") {
                        val ownerId = Firebase.auth.currentUser?.uid ?: return@composable
                        EditProfileScreen(
                            onSaveChanges = { name, cuisine, imageUrl, deliveryLocations ->
                                val updates = mapOf(
                                    "name" to name,
                                    "cuisine" to cuisine,
                                    "imageUrl" to imageUrl,
                                    "deliveryLocations" to deliveryLocations // Updated
                                )
                                Firebase.firestore.collection("restaurants").document(ownerId)
                                    .update(updates)
                                    .addOnSuccessListener {
                                        Toast.makeText(applicationContext, "Profile Updated", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    }
                            }
                        )
                    }

                    // ... (rest of the NavHost remains the same)

                    composable("create_category") {
                        val ownerId = Firebase.auth.currentUser?.uid ?: return@composable
                        CreateCategoryScreen(
                            onSaveCategory = { categoryName, startTime, endTime, deliveryTime ->
                                val categoryData = hashMapOf(
                                    "name" to categoryName, "startTime" to startTime,
                                    "endTime" to endTime, "deliveryTime" to deliveryTime
                                )
                                Firebase.firestore.collection("restaurants").document(ownerId)
                                    .collection("preOrderCategories")
                                    .add(categoryData)
                                    .addOnSuccessListener {
                                        navController.popBackStack()
                                    }
                            }
                        )
                    }

                    composable(
                        "category_detail/{categoryName}",
                        arguments = listOf(navArgument("categoryName") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val ownerId = Firebase.auth.currentUser?.uid ?: return@composable
                        val encodedCategoryName = backStackEntry.arguments?.getString("categoryName") ?: ""
                        val categoryName = URLDecoder.decode(encodedCategoryName, StandardCharsets.UTF_8.toString())

                        CategoryDetailScreen(
                            categoryName = categoryName,
                            onNavigateToAddItem = { category ->
                                val encodedCategory = URLEncoder.encode(category, StandardCharsets.UTF_8.toString())
                                navController.navigate("add_item/$encodedCategory")
                            },
                            onNavigateToOrderList = { category ->
                                val encodedCategory = URLEncoder.encode(category, StandardCharsets.UTF_8.toString())
                                navController.navigate("order_list/$encodedCategory")
                            },
                            onDeleteItem = { itemId ->
                                deleteMenuItem(ownerId, itemId)
                            }
                        )
                    }

                    composable(
                        "order_list/{categoryName}",
                        arguments = listOf(navArgument("categoryName") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val encodedCategoryName = backStackEntry.arguments?.getString("categoryName") ?: ""
                        val categoryName = URLDecoder.decode(encodedCategoryName, StandardCharsets.UTF_8.toString())
                        OrderListScreen(
                            categoryName = categoryName,
                            onBackClicked = { navController.popBackStack() },
                            onAcceptOrder = { orderId -> updateOrderStatus(orderId, "Accepted") },
                            onRejectOrder = { orderId -> updateOrderStatus(orderId, "Rejected") },
                            onAcceptAllOrders = { orderIds -> updateAllOrdersStatus(orderIds, "Accepted") },
                            onRejectAllOrders = { orderIds -> updateAllOrdersStatus(orderIds, "Rejected") }
                        )
                    }

                    composable(
                        "add_item/{category}",
                        arguments = listOf(navArgument("category") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val ownerId = Firebase.auth.currentUser?.uid ?: return@composable
                        val encodedCategory = backStackEntry.arguments?.getString("category") ?: "Current Menu"
                        val category = URLDecoder.decode(encodedCategory, StandardCharsets.UTF_8.toString())

                        AddMenuItemScreen(
                            category = category,
                            onSaveItemClicked = { itemName, price ->
                                // In MainActivity.kt, inside composable("add_item/{category}")

                                val newItem = hashMapOf(
                                    "name" to itemName,
                                    // Use the Elvis operator to default to 0.0 if conversion to Double fails
                                    "price" to (price.toDoubleOrNull() ?: 0.0),
                                    "category" to category
                                )
                                Firebase.firestore.collection("restaurants").document(ownerId)
                                    .collection("menuItems")
                                    .add(newItem)
                                    .addOnSuccessListener {
                                        Toast.makeText(applicationContext, "$itemName added!", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    }
                            }
                        )
                    }
                }
            }
        }
    }

    // ... (rest of MainActivity functions remain the same)

    private fun checkRestaurantProfile(userId: String, navController: NavController) {
        val db = Firebase.firestore
        db.collection("restaurants").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    navController.navigate("dashboard") {
                        popUpTo("auth") { inclusive = true }
                    }
                } else {
                    navController.navigate("create_profile") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            }
    }

    private fun deleteMenuItem(ownerId: String, itemId: String) {
        Firebase.firestore.collection("restaurants").document(ownerId)
            .collection("menuItems").document(itemId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(applicationContext, "Item deleted", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteCategory(ownerId: String, categoryId: String, categoryName: String) {
        val db = Firebase.firestore
        val restaurantRef = db.collection("restaurants").document(ownerId)

        restaurantRef.collection("menuItems").whereEqualTo("category", categoryName).get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                for (document in snapshot.documents) {
                    batch.delete(document.reference)
                }
                batch.commit().addOnSuccessListener {
                    restaurantRef.collection("preOrderCategories").document(categoryId)
                        .delete()
                        .addOnSuccessListener {
                            Toast.makeText(applicationContext, "Category deleted", Toast.LENGTH_SHORT).show()
                        }
                }
            }
    }

    private fun updateOrderStatus(orderId: String, newStatus: String) {
        Firebase.firestore.collection("orders").document(orderId)
            .update("orderStatus", newStatus)
            .addOnSuccessListener {
                Toast.makeText(applicationContext, "Order marked as $newStatus", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateAllOrdersStatus(orderIds: List<String>, newStatus: String) {
        if (orderIds.isEmpty()) return
        val db = Firebase.firestore
        val batch = db.batch()
        orderIds.forEach { orderId ->
            val docRef = db.collection("orders").document(orderId)
            batch.update(docRef, "orderStatus", newStatus)
        }
        batch.commit().addOnSuccessListener {
            Toast.makeText(applicationContext, "${orderIds.size} orders marked as $newStatus", Toast.LENGTH_SHORT).show()
        }
    }
}
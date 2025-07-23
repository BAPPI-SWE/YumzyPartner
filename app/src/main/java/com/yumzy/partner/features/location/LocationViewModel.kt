package com.yumzy.partner.features.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// This data class will hold all the state for our UI
data class LocationUiState(
    val allLocations: Map<String, List<String>> = emptyMap(),
    val baseLocationOptions: List<String> = emptyList(),
    // Use a map to track selected sub-locations for each base location
    val selectedSubLocations: Map<String, List<String>> = emptyMap()
)

class LocationViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState = _uiState.asStateFlow()

    init {
        fetchLocations()
    }

    private fun fetchLocations() {
        viewModelScope.launch {
            Firebase.firestore.collection("locations").get()
                .addOnSuccessListener { snapshot ->
                    val locationMap = mutableMapOf<String, List<String>>()
                    snapshot.documents.forEach { doc ->
                        val baseName = doc.getString("name") ?: ""
                        val subLocations = doc.get("subLocations") as? List<String> ?: emptyList()
                        if (baseName.isNotBlank()) {
                            locationMap[baseName] = subLocations
                        }
                    }
                    _uiState.update {
                        it.copy(
                            allLocations = locationMap,
                            baseLocationOptions = locationMap.keys.toList()
                        )
                    }
                }
        }
    }

    // Load previously saved selections
    fun setInitialSelections(deliveryLocations: List<String>) {
        val newSelections = mutableMapOf<String, List<String>>()
        _uiState.value.allLocations.forEach { (base, subs) ->
            val selected = subs.filter { deliveryLocations.contains(it) }
            if (selected.isNotEmpty()) {
                newSelections[base] = selected
            }
        }
        _uiState.update { it.copy(selectedSubLocations = newSelections) }
    }

    // Toggle the selection of a sub-location
    fun onSubLocationToggled(baseLocation: String, subLocation: String) {
        val currentSelections = _uiState.value.selectedSubLocations[baseLocation]?.toMutableList() ?: mutableListOf()
        if (currentSelections.contains(subLocation)) {
            currentSelections.remove(subLocation)
        } else {
            currentSelections.add(subLocation)
        }
        _uiState.update {
            it.copy(
                selectedSubLocations = it.selectedSubLocations.toMutableMap().apply {
                    if (currentSelections.isEmpty()) remove(baseLocation) else this[baseLocation] = currentSelections
                }
            )
        }
    }

    // Get a flat list of all selected delivery locations
    fun getFinalDeliveryLocations(): List<String> {
        return _uiState.value.selectedSubLocations.values.flatten()
    }
}
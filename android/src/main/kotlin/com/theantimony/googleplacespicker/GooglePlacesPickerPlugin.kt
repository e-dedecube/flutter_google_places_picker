package com.theantimony.googleplacespicker

import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Bitmap
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.model.TypeFilter
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.io.ByteArrayOutputStream

class GooglePlacesPickerPlugin : FlutterPlugin, MethodCallHandler, PluginRegistry.ActivityResultListener, ActivityAware {
    private var mActivity: Activity? = null
    private var mChannel: MethodChannel? = null
    private var mBinding: ActivityPluginBinding? = null
    private var mPlace: PlacesClient? = null
    private var mResult: Result? = null

    private val mFilterTypes = mapOf(
        "address" to TypeFilter.ADDRESS,
        "cities" to TypeFilter.CITIES,
        "establishment" to TypeFilter.ESTABLISHMENT,
        "geocode" to TypeFilter.GEOCODE,
        "regions" to TypeFilter.REGIONS
    )

    companion object {
        const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 57864
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        mChannel = MethodChannel(binding.binaryMessenger, "plugin_google_place_picker")
        mChannel?.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        mChannel?.setMethodCallHandler(null)
        mChannel = null
        mActivity = null
        mPlace = null
        mBinding?.removeActivityResultListener(this)
        mBinding = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        mActivity = binding.activity
        mBinding = binding
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivity() {
        mBinding?.removeActivityResultListener(this)
        mActivity = null
        mBinding = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        mResult = result
        when (call.method) {
            "showAutocomplete" -> {
                showAutocompletePicker(
                    call.argument("mode"),
                    call.argument("bias"),
                    call.argument("restriction"),
                    call.argument("type"),
                    call.argument("country")
                )
            }

            "initialize" -> {
                initialize(call.argument("androidApiKey"))
            }

            else -> result.notImplemented()
        }
    }

    private fun initialize(apiKey: String?) {
        if (apiKey.isNullOrEmpty()) {
            mResult?.error("API_KEY_ERROR", "Invalid Android API Key", null)
            return
        }

        try {
            if (!Places.isInitialized()) {
                mActivity?.applicationContext?.let {
                    Places.initialize(it, apiKey)
                    mPlace = Places.createClient(it)
                }
            }
            mResult?.success(null)
        } catch (e: Exception) {
            mResult?.error("API_KEY_ERROR", e.localizedMessage, null)
        }
    }

    private fun showAutocompletePicker(
        mode: Int?,
        bias: HashMap<String, Double>?,
        restriction: HashMap<String, Double>?,
        type: String?,
        country: String?
    ) {
        val modeToUse = mode ?: 71
        val fields = listOf(
            Place.Field.ID, Place.Field.ADDRESS, Place.Field.NAME, Place.Field.LAT_LNG,
            Place.Field.PHONE_NUMBER, Place.Field.WEBSITE_URI, Place.Field.OPENING_HOURS,
            Place.Field.TYPES, Place.Field.PHOTO_METADATAS, Place.Field.ADDRESS_COMPONENTS
        )

        var intentBuilder = Autocomplete.IntentBuilder(
            if (modeToUse == 71) AutocompleteActivityMode.OVERLAY else AutocompleteActivityMode.FULLSCREEN,
            fields
        )

        bias?.let {
            val locationBias = RectangularBounds.newInstance(
                LatLng(it["southWestLat"] ?: 0.0, it["southWestLng"] ?: 0.0),
                LatLng(it["northEastLat"] ?: 0.0, it["northEastLng"] ?: 0.0)
            )
            intentBuilder = intentBuilder.setLocationBias(locationBias)
        }

        restriction?.let {
            val locationRestriction = RectangularBounds.newInstance(
                LatLng(it["southWestLat"] ?: 0.0, it["southWestLng"] ?: 0.0),
                LatLng(it["northEastLat"] ?: 0.0, it["northEastLng"] ?: 0.0)
            )
            intentBuilder = intentBuilder.setLocationRestriction(locationRestriction)
        }

        type?.let {
            intentBuilder = intentBuilder.setTypeFilter(mFilterTypes[it])
        }

        country?.let {
            intentBuilder = intentBuilder.setCountry(it)
        }

        mActivity?.let {
            val intent = intentBuilder.build(it)
            try {
                it.startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE)
            } catch (e: GooglePlayServicesNotAvailableException) {
                mResult?.error("GooglePlayServicesNotAvailableException", e.message, null)
            } catch (e: GooglePlayServicesRepairableException) {
                mResult?.error("GooglePlayServicesRepairableException", e.message, null)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != PLACE_AUTOCOMPLETE_REQUEST_CODE) return false

        when (resultCode) {
            RESULT_OK -> {
                val place = Autocomplete.getPlaceFromIntent(data!!)
                val placeMap = mutableMapOf<String, Any>(
                    "latitude" to (place.latLng?.latitude ?: 0.0),
                    "longitude" to (place.latLng?.longitude ?: 0.0),
                    "id" to (place.id ?: ""),
                    "name" to (place.name ?: ""),
                    "address" to (place.address ?: "")
                )
                place.phoneNumber?.let { placeMap["phoneNumber"] = it }
                place.websiteUri?.let { placeMap["website"] = it.toString() }
                place.types?.map { it.toString() }?.let { placeMap["types"] = it }
                place.openingHours?.weekdayText?.let { placeMap["openingHoursWeekday"] = it }

                place.addressComponents?.let { components ->
                    var locality = ""
                    var province1 = ""
                    var province2 = ""
                    var province3 = ""
                    var country = ""
                    for (comp in components.asList()) {
                        val types = comp.types
                        val name = comp.name
                        if ("locality" in types && locality.isEmpty()) locality = name
                        if ("country" in types) country = name
                        if ("postal_town" in types && locality.isEmpty()) locality = name
                        if ("administrative_area_level_3" in types && locality.isEmpty()) locality = name
                        if ("administrative_area_level_2" in types && locality.isEmpty()) locality = name
                        if ("administrative_area_level_1" in types && locality.isEmpty()) locality = name
                        if ("establishment" in types && locality.isEmpty()) locality = name
                        if ("natural_feature" in types && locality.isEmpty()) locality = name

                        if ("administrative_area_level_1" in types) province1 = name
                        if ("administrative_area_level_2" in types) province2 = name
                        if ("administrative_area_level_3" in types) province3 = name
                    }
                    placeMap["locality"] = locality
                    placeMap["province1"] = province1
                    placeMap["province2"] = province2
                    placeMap["province3"] = province3
                    placeMap["country"] = country
                }

                place.photoMetadatas?.firstOrNull()?.let { metadata ->
                    val photoRequest = FetchPhotoRequest.builder(metadata).build()
                    mPlace?.fetchPhoto(photoRequest)?.addOnSuccessListener { photo ->
                        val bitmap = photo.bitmap
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        val byteArray = stream.toByteArray()
                        bitmap.recycle()
                        placeMap["photo"] = byteArray
                        mResult?.success(placeMap)
                    }
                } ?: run {
                    mResult?.success(placeMap)
                }
            }

            AutocompleteActivity.RESULT_ERROR -> {
                val status = Autocomplete.getStatusFromIntent(data!!)
                mResult?.error("PLACE_AUTOCOMPLETE_ERROR", status.statusMessage, null)
            }

            RESULT_CANCELED -> {
                mResult?.error("USER_CANCELED", "User has canceled the operation.", null)
            }

            else -> {
                mResult?.error("UNKNOWN", "Unknown error.", null)
            }
        }
        return true
    }
}
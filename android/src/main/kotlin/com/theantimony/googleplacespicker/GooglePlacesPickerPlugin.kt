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
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.io.ByteArrayOutputStream
import java.lang.Exception

class GooglePlacesPickerPlugin() : FlutterPlugin, MethodCallHandler, PluginRegistry.ActivityResultListener, ActivityAware {
    var mActivity: Activity? = null
    var mChannel: MethodChannel? = null
    var mBinding: ActivityPluginBinding? = null
    var mPlace: PlacesClient? = null

    private var mResult: Result? = null
    private val mFilterTypes = mapOf(
            Pair("address", TypeFilter.ADDRESS),
            Pair("cities", TypeFilter.CITIES),
            Pair("establishment", TypeFilter.ESTABLISHMENT),
            Pair("geocode", TypeFilter.GEOCODE),
            Pair("regions", TypeFilter.REGIONS)
    )

    companion object {
        const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 57864

        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val instance = GooglePlacesPickerPlugin().apply {
                mActivity = registrar.activity()
            }
            registrar.addActivityResultListener(instance)
            instance.onAttachedToEngine(registrar.messenger())
        }
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        onAttachedToEngine(binding.binaryMessenger)
    }

    private fun onAttachedToEngine(messenger: BinaryMessenger) {
        mChannel = MethodChannel(messenger, "plugin_google_place_picker")
        mChannel?.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        mResult = result
        if (call.method.equals("showAutocomplete")) {
            showAutocompletePicker(
                    call.argument("mode"),
                    call.argument("bias"),
                    call.argument("restriction"),
                    call.argument("type"),
                    call.argument("country")
            )
        } else if (call.method.equals("initialize")) {
            initialize(call.argument("androidApiKey"))
        } else {
            result.notImplemented()
        }
    }

    fun initialize(apiKey: String?) {
        if (apiKey.isNullOrEmpty()) {
            mResult?.error("API_KEY_ERROR", "Invalid Android API Key", null)
            return
        }
        try {
            if (!Places.isInitialized()) {
                mActivity?.let {
                    Places.initialize(it.applicationContext, apiKey)
                    mPlace = Places.createClient(it.applicationContext)
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
                Place.Field.ID,
                Place.Field.ADDRESS,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.PHONE_NUMBER,
                Place.Field.WEBSITE_URI,
                Place.Field.OPENING_HOURS,
                Place.Field.TYPES,
                Place.Field.PHOTO_METADATAS,
                Place.Field.ADDRESS_COMPONENTS
                )
        var intentBuilder = Autocomplete.IntentBuilder(if (modeToUse == 71) AutocompleteActivityMode.OVERLAY else AutocompleteActivityMode.FULLSCREEN, fields)

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

    override fun onActivityResult(p0: Int, p1: Int, p2: Intent?): Boolean {
        if (p0 != PLACE_AUTOCOMPLETE_REQUEST_CODE) {
            return false
        }
        if (p1 == RESULT_OK && p2 != null) {
            val place = Autocomplete.getPlaceFromIntent(p2)
            val placeMap = mutableMapOf<String, Any>()
            placeMap.put("latitude", place.latLng?.latitude ?: 0.0)
            placeMap.put("longitude", place.latLng?.longitude ?: 0.0)
            placeMap.put("id", place.id ?: "")
            placeMap.put("name", place.name ?: "")
            placeMap.put("address", place.address ?: "")
            if (!place.phoneNumber.isNullOrEmpty()) {
                placeMap.put("phoneNumber", place.phoneNumber ?: "")
            }
            if (place.websiteUri != null) {
                placeMap.put("website", place.websiteUri.toString() ?: "")
            }
            val arrayTypesTransformed = place.types
            if (arrayTypesTransformed != null) {
                val transformed = arrayTypesTransformed.map { t -> t.toString() }

                placeMap.put("types", transformed ?: "")
            }
            val openingHours = place.openingHours
            if (openingHours != null) {
                val transformed = openingHours.weekdayText

                placeMap.put("openingHoursWeekday", transformed ?: "")
            }
            val addressComponents = place.addressComponents
            if (addressComponents != null) {
                var locality: String = ""
                var province1: String = ""
                var province2: String = ""
                var province3: String = ""
                var country: String = ""
                for (address in addressComponents.asList()) {
                    if (address.types.contains("locality")) {
                        val transformed = address.name
                        locality = transformed
                    } else if (address.types.contains("country")) {
                        val transformed = address.name
                        country = transformed
                    } else if (address.types.contains("postal_town") && locality == "") {
                        val transformed = address.name
                        locality = transformed
                    } else if (address.types.contains("administrative_area_level_3") && locality == "") {
                        val transformed = address.name
                        locality = transformed
                    } else if (address.types.contains("administrative_area_level_2") && locality == "") {
                        val transformed = address.name
                        locality = transformed
                    } else if (address.types.contains("administrative_area_level_1") && locality == "") {
                        val transformed = address.name
                        locality = transformed
                    } else if (address.types.contains("establishment") && locality == "") {
                        val transformed = address.name
                        locality = transformed
                    } else if (address.types.contains("natural_feature") && locality == "") {
                        val transformed = address.name
                        locality = transformed
                    }                    
                    if (address.types.contains("administrative_area_level_1")) {
                        val transformed = address.name
                        province1 = transformed
                    }
                    if (address.types.contains("administrative_area_level_2")) {
                        val transformed = address.name
                        province2 = transformed
                    }
                    if (address.types.contains("administrative_area_level_3")) {
                        val transformed = address.name
                        province3 = transformed
                    }
                }
                placeMap.put("locality", locality ?: "")
                placeMap.put("province1", province1 ?: "")
                placeMap.put("province2", province2 ?: "")
                placeMap.put("province3", province3 ?: "")
                placeMap.put("country", country ?: "")
            }
            val photoMetadatas = place.photoMetadatas
            if (photoMetadatas != null) {
                val photoRequest = FetchPhotoRequest.builder(photoMetadatas[0]).build()
                mPlace?.fetchPhoto(photoRequest)?.addOnSuccessListener { fetchPhotoResponse ->
                    val bitmap = fetchPhotoResponse.getBitmap()
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val byteArray = stream.toByteArray()
                    bitmap.recycle()
                    placeMap.put("photo", byteArray)
                    mResult?.success(placeMap)
                }
            } else {
                mResult?.success(placeMap)
            }
        } else if (p1 == AutocompleteActivity.RESULT_ERROR && p2 != null) {
            val status = Autocomplete.getStatusFromIntent(p2)
            mResult?.error("PLACE_AUTOCOMPLETE_ERROR", status.statusMessage, null)
        } else if (p1 == RESULT_CANCELED) {
            mResult?.error("USER_CANCELED", "User has canceled the operation.", null)
        } else {
            mResult?.error("UNKNOWN", "Unknown error.", null)
        }
        return true
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        mActivity = null
        mChannel?.setMethodCallHandler(null)
        mBinding?.removeActivityResultListener(this)
        mChannel = null
        mBinding = null
    }

    override fun onDetachedFromActivity() {
        mActivity = null
        mBinding?.removeActivityResultListener(this)
        mBinding = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        mActivity = binding.activity
        mBinding = binding
        binding.addActivityResultListener(this)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        mActivity = binding.activity
        mBinding = binding
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        mActivity = null
        mBinding?.removeActivityResultListener(this)
        mBinding = null
    }
}

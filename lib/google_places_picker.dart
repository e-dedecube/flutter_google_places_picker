import 'dart:async';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class Place {
  double latitude;
  double longitude;
  String id;
  String name;
  String address;
  String phoneNumber;
  String website;
  List<String> openingHoursWeekday;
  List<String> types;
  Image photo;
  Map info;
  String locality;
  String country;
}

enum PlaceAutocompleteMode { MODE_OVERLAY, MODE_FULLSCREEN }

enum TypeFilter { ADDRESS, CITIES, ESTABLISHMENT, GEOCODE, REGIONS, BOTH }

class LocationBias {
  double northEastLat;
  double northEastLng;
  double southWestLat;
  double southWestLng;
}

class LocationRestriction {
  double northEastLat;
  double northEastLng;
  double southWestLat;
  double southWestLng;
}

class PluginGooglePlacePicker {
  static const MethodChannel _channel =
      const MethodChannel('plugin_google_place_picker');

  static Future<Place> showAutocomplete({
    PlaceAutocompleteMode mode,
    LocationBias bias,
    LocationRestriction restriction,
    TypeFilter typeFilter,
    String countryCode,
  }) async {
    var argMap = {
      "mode": mode == PlaceAutocompleteMode.MODE_OVERLAY ? 71 : 72,
      "bias": _convertLocationBiasToMap(bias),
      "restriction": _convertLocationRestrictionToMap(restriction),
      "type": _convertFilterTypeToString(typeFilter),
      "country": countryCode
    };
    final Map placeMap =
        await _channel.invokeMethod('showAutocomplete', argMap);
    return _initPlaceFromMap(placeMap);
  }

  static Future<void> initialize(
      {String androidApiKey, String iosApiKey}) async {
    await _channel.invokeMethod(
        'initialize', {"androidApiKey": androidApiKey, "iosApiKey": iosApiKey});
  }

  static Place _initPlaceFromMap(Map placeMap) {
    Place place = Place();
    place.name = placeMap["name"];
    place.id = placeMap["id"];
    place.info = placeMap;
    place.address = placeMap["address"];
    if (placeMap.containsKey("phoneNumber")) {
      place.phoneNumber = placeMap["phoneNumber"];
    }
    if (placeMap.containsKey("website")) {
      place.website = placeMap["website"];
    }
    if (placeMap.containsKey("photo")) {
      place.photo = Image.memory(placeMap["photo"]);
    }
    if (placeMap.containsKey("types")) {
      place.types = placeMap["types"].cast<String>();
    }
    if (placeMap.containsKey("openingHoursWeekday")) {
      place.openingHoursWeekday =
          placeMap["openingHoursWeekday"].cast<String>();
    }
    if (placeMap["latitude"] is double) {
      place.latitude = placeMap["latitude"];
      place.longitude = placeMap["longitude"];
    } else {
      place.latitude = double.parse(placeMap["latitude"]);
      place.longitude = double.parse(placeMap["longitude"]);
    }
    if (placeMap.containsKey("locality")) {
      place.locality = placeMap["locality"];
    }
    if (placeMap.containsKey("country")) {
      place.country = placeMap["country"];
    }
    return place;
  }

  static String _convertFilterTypeToString(TypeFilter type) {
    if (type == null) {
      return null;
    }
    switch (type) {
      case TypeFilter.ADDRESS:
        return "address";
      case TypeFilter.CITIES:
        return "cities";
      case TypeFilter.ESTABLISHMENT:
        return "establishment";
      case TypeFilter.GEOCODE:
        return "geocode";
      case TypeFilter.REGIONS:
        return "regions";
      case TypeFilter.BOTH:
        return "geocode|establishment";
    }
    return "";
  }

  static Map<String, double> _convertLocationBiasToMap(LocationBias bias) {
    if (bias == null) {
      return null;
    }
    return {
      "southWestLat": bias.southWestLat ?? 0.0,
      "southWestLng": bias.southWestLng ?? 0.0,
      "northEastLat": bias.northEastLat ?? 90.0,
      "northEastLng": bias.northEastLng ?? 180.0
    };
  }

  static Map<String, double> _convertLocationRestrictionToMap(
      LocationRestriction restriction) {
    if (restriction == null) {
      return null;
    }
    return {
      "southWestLat": restriction.southWestLat ?? 0.0,
      "southWestLng": restriction.southWestLng ?? 0.0,
      "northEastLat": restriction.northEastLat ?? 90.0,
      "northEastLng": restriction.northEastLng ?? 180.0
    };
  }
}

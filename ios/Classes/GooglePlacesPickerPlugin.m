#import "GooglePlacesPickerPlugin.h"
@import GoogleMaps;
@import GooglePlaces;

@implementation GooglePlacesPickerPlugin
FlutterResult _result;
UIViewController *vc;
NSDictionary *filterTypes;

+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
    filterTypes = @{
                    @"address": [NSNumber numberWithInt:kGMSPlacesAutocompleteTypeFilterAddress],
                    @"cities": [NSNumber numberWithInt:kGMSPlacesAutocompleteTypeFilterCity],
                    @"region": [NSNumber numberWithInt:kGMSPlacesAutocompleteTypeFilterRegion],
                    @"geocode": [NSNumber numberWithInt:kGMSPlacesAutocompleteTypeFilterGeocode],
                    @"establishment": [NSNumber numberWithInt:kGMSPlacesAutocompleteTypeFilterEstablishment]
                    };
    
    vc = [UIApplication sharedApplication].delegate.window.rootViewController;
  FlutterMethodChannel* channel = [FlutterMethodChannel
      methodChannelWithName:@"plugin_google_place_picker"
            binaryMessenger:[registrar messenger]];
  GooglePlacesPickerPlugin* instance = [[GooglePlacesPickerPlugin alloc] init];
  [registrar addMethodCallDelegate:instance channel:channel];
}

- (void)handleMethodCall:(FlutterMethodCall*)call result:(FlutterResult)result {
    _result = result;
  if ([@"showAutocomplete" isEqualToString:call.method]) {
      [self showAutocomplete:call.arguments[@"type"]
                     bounds:call.arguments[@"bounds"]
                     restriction:call.arguments[@"restriction"]
                     country:call.arguments[@"country"]];
  } else if ([@"initialize" isEqualToString:call.method]) {
      [self initialize:call.arguments[@"iosApiKey"]];
  } else {
    result(FlutterMethodNotImplemented);
  }
}

-(void)initialize:(NSString *)apiKey {
    if ([apiKey length] == 0) {
        FlutterError *fError = [FlutterError errorWithCode:@"API_KEY_ERROR" message:@"Invalid iOS API Key" details:nil];
        _result(fError);
    }
    [GMSPlacesClient provideAPIKey:apiKey];
    [GMSServices provideAPIKey:apiKey];
    _result(nil);
}

-(void)showAutocomplete:(NSString *)filter bounds:(NSDictionary *)boundsDictionary restriction:(NSDictionary *)restriction country:(NSString *)country {
    
    GMSAutocompleteViewController *autocompleteController = [[GMSAutocompleteViewController alloc] init];
    
    if (![filter isEqual:[NSNull null]] || ![country isEqual:[NSNull null]]) {
        GMSAutocompleteFilter *autocompleteFilter = [[GMSAutocompleteFilter alloc] init];
        if (![filter isEqual:[NSNull null]]) {
            autocompleteFilter.type = [filterTypes[filter] intValue];
        } else {
            autocompleteFilter.type = kGMSPlacesAutocompleteTypeFilterNoFilter;
        }
        
        if (![country isEqual:[NSNull null]]) {
            autocompleteFilter.country = country;
        }
        
        autocompleteController.autocompleteFilter = autocompleteFilter;
        
    }
    
    if (![boundsDictionary isEqual:[NSNull null]] || ![restriction isEqual:[NSNull null]]) {
        double neLat;
        double neLng;
        double swLat;
        double swLng;
        
        if (![restriction isEqual:[NSNull null]]) {
            neLat = [restriction[@"northEastLat"] doubleValue];
            neLng = [restriction[@"northEastLng"] doubleValue];
            swLat = [restriction[@"southWestLat"] doubleValue];
            swLng = [restriction[@"southWestLng"] doubleValue];
            autocompleteController.autocompleteBoundsMode = kGMSAutocompleteBoundsModeRestrict;
        } else {
            neLat = [boundsDictionary[@"northEastLat"] doubleValue];
            neLng = [boundsDictionary[@"northEastLng"] doubleValue];
            swLat = [boundsDictionary[@"southWestLat"] doubleValue];
            swLng = [boundsDictionary[@"southWestLng"] doubleValue];
        }
        CLLocationCoordinate2D neCoordinate = CLLocationCoordinate2DMake(neLat, neLng);
        CLLocationCoordinate2D swCoordinate = CLLocationCoordinate2DMake(swLat, swLng);
        
        // GMSCoordinateBounds *bounds = [[GMSCoordinateBounds alloc] initWithCoordinate:neCoordinate coordinate:swCoordinate];
        // autocompleteController.autocompleteBounds = bounds;
        
    }
    
    autocompleteController.delegate = self;
    UIViewController *vc = [UIApplication sharedApplication].delegate.window.rootViewController;
    [vc presentViewController:autocompleteController animated:YES completion:nil];
    
}

- (void)viewController:(nonnull GMSAutocompleteViewController *)viewController didAutocompleteWithPlace:(nonnull GMSPlace *)place {
    [vc dismissViewControllerAnimated:YES completion:nil];
    NSMutableDictionary *placeMap = [NSMutableDictionary dictionaryWithObject:place.name forKey:@"name"];
    [placeMap setObject:[NSString stringWithFormat:@"%.7f", place.coordinate.latitude] forKey:@"latitude"];
    [placeMap setObject:[NSString stringWithFormat:@"%.7f", place.coordinate.longitude] forKey:@"longitude"];
    [placeMap setObject:place.placeID forKey:@"id"];
    if (place.phoneNumber != nil) {
        [placeMap setObject:place.phoneNumber forKey:@"phoneNumber"];
    }
    if (place.website != nil) {
        [placeMap setObject:place.website.absoluteString forKey:@"website"];
    }
    if (place.openingHours != nil) {
        GMSOpeningHours *openingHours = place.openingHours;
        NSArray *weekdayText = openingHours.weekdayText;
        [placeMap setObject:weekdayText forKey:@"openingHoursWeekday"];
    }
    if (place.types != nil) {
        [placeMap setObject:place.types forKey:@"types"];
    }
    if (place.formattedAddress != nil) {
        [placeMap setObject:place.formattedAddress forKey:@"address"];
    }

    if (place.addressComponents != nil) {
        NSString *locality = @"";
         NSString *province1 = @"";
         NSString *province2 = @"";
         NSString *province3 = @"";
        NSString *country = @"";
        for (GMSAddressComponent *component in place.addressComponents) {
            NSArray *types = component.types;
            if ([types containsObject:@"locality"]) {
               locality = component.name;
            } else if ([types containsObject:@"country"]) {
               country = component.name;
            } else if ([types containsObject:@"postal_town"] && [locality isEqualToString:@""]) {
               locality = component.name;
            } else if ([types containsObject:@"administrative_area_level_3"] && [locality isEqualToString:@""]) {
               locality = component.name;
            } else if ([types containsObject:@"administrative_area_level_2"] && [locality isEqualToString:@""]) {
               locality = component.name;
            } else if ([types containsObject:@"administrative_area_level_1"] && [locality isEqualToString:@""]) {
               locality = component.name;
            }
            if ([types containsObject:@"administrative_area_level_1"]) {
               province1 = component.name;
            } 
            if ([types containsObject:@"administrative_area_level_2"]) {
               province2 = component.name;
            } 
            if ([types containsObject:@"administrative_area_level_3"]) {
               province3 = component.name;
            }
        }
        [placeMap setObject:locality forKey:@"locality"];
        [placeMap setObject:province1 forKey:@"province1"];
        [placeMap setObject:province2 forKey:@"province2"];
        [placeMap setObject:province3 forKey:@"province3"];
        [placeMap setObject:country forKey:@"country"];
    }
                  
    if (place.photos != nil) {
        [[GMSPlacesClient sharedClient] loadPlacePhoto:place.photos[0] callback:^(UIImage * _Nullable photo, NSError * _Nullable error) {
          if (error == nil) {
              NSData* data = UIImagePNGRepresentation(photo);
              if (data) {
                  [placeMap setObject:[FlutterStandardTypedData typedDataWithBytes:data] forKey:@"photo"];
                  
                  NSMutableDictionary *mutablePlaceMap = placeMap.mutableCopy;
                  _result(mutablePlaceMap);
              }
          }
        }];
    } else {
        NSMutableDictionary *mutablePlaceMap = placeMap.mutableCopy;
        _result(mutablePlaceMap);
    }
}

- (void)viewController:(nonnull GMSAutocompleteViewController *)viewController didFailAutocompleteWithError:(nonnull NSError *)error {
    [vc dismissViewControllerAnimated:YES completion:nil];
    FlutterError *fError = [FlutterError errorWithCode:@"PLACE_AUTOCOMPLETE_ERROR" message:error.localizedDescription details:nil];
    
    _result(fError);
}

- (void)wasCancelled:(nonnull GMSAutocompleteViewController *)viewController {
    [vc dismissViewControllerAnimated:YES completion:nil];
    FlutterError *fError = [FlutterError errorWithCode:@"USER_CANCELED" message:@"User has canceled the operation." details:nil];
    _result(fError);
}

- (void)didRequestAutocompletePredictions:(GMSAutocompleteViewController *)viewController {
    [UIApplication sharedApplication].networkActivityIndicatorVisible = YES;
}

- (void)didUpdateAutocompletePredictions:(GMSAutocompleteViewController *)viewController {
    [UIApplication sharedApplication].networkActivityIndicatorVisible = NO;
}

@end

#import "AppDelegate+FCMPlugin.h"
#import "FCMPlugin.h"
#import "FCMPluginIOS9Support.h"
#import "FCMNotificationCenterDelegate.h"
#import <objc/runtime.h>
#import <Foundation/Foundation.h>
#import <CocoaLumberjack/CocoaLumberjack.h>

#define ddLogLevel DDLogLevelAll

@import UserNotifications;
@import Firebase;

// Implement UNUserNotificationCenterDelegate to receive display notification via APNS for devices
// running iOS 10 and above. Implement FIRMessagingDelegate to receive data message via FCM for
// devices running iOS 10 and above.
@interface AppDelegate () <FIRMessagingDelegate>
@end

@implementation AppDelegate (MCPlugin)

static NSDictionary *lastPush;
static NSDictionary *initialPushPayload;
static NSString *fcmToken;
static NSString *apnsToken;
NSString *const kGCMMessageIDKey = @"gcm.message_id";
NSString *const kHRSPendingDataNotificationsKey = @"HRSPendingDataNotifications";
FCMNotificationCenterDelegate *notificationCenterDelegate;

//Method swizzling
+ (void)load {
    Method original =  class_getInstanceMethod(self, @selector(application:didFinishLaunchingWithOptions:));
    Method custom =    class_getInstanceMethod(self, @selector(application:customDidFinishLaunchingWithOptions:));
    method_exchangeImplementations(original, custom);
}

- (BOOL)application:(UIApplication *)application customDidFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    [self application:application customDidFinishLaunchingWithOptions:launchOptions];

    DDLogDebug(@"DidFinishLaunchingWithOptions");
    if ([UNUserNotificationCenter class] != nil) {
        // For iOS 10 display notification (sent via APNS)
        notificationCenterDelegate = [NSClassFromString(@"FCMNotificationCenterDelegate") alloc];
        [notificationCenterDelegate configureForNotifications];
    }
    // For iOS message (sent via FCM)
    // Fixed DEV-15364
    // Setting delegate to track firebase events.
    [FIRMessaging messaging].delegate = self;

    [self performSelector:@selector(configureForNotifications) withObject:self afterDelay:0.3f];

    return YES;
}

- (void)configureForNotifications {
    if([FIRApp defaultApp] == nil) {
        [FIRApp configure];
    }
}

+ (void)requestPushPermission:(void (^)(BOOL yesOrNo, NSError* _Nullable error))block withOptions:(NSDictionary*)options {
    if ([UNUserNotificationCenter class] == nil) {
        return [FCMPluginIOS9Support requestPushPermission:block withOptions:options];
    }
    UNAuthorizationOptions authOptions = UNAuthorizationOptionAlert | UNAuthorizationOptionSound | UNAuthorizationOptionBadge;
        [[UNUserNotificationCenter currentNotificationCenter] requestAuthorizationWithOptions:authOptions completionHandler:^(BOOL granted, NSError* _Nullable error) {
        if (granted) {
            dispatch_async(dispatch_get_main_queue(), ^{
                [[UIApplication sharedApplication] registerForRemoteNotifications];
            });
            block(YES, error);
            return;
        }
        DDLogDebug(@"User Notification permission denied: %@", error.localizedDescription);
        block(NO, error);
    }];
}

- (void)application:(UIApplication *)application didRegisterForRemoteNotificationsWithDeviceToken:(NSData *)deviceTokenData {
    [FIRMessaging messaging].APNSToken = deviceTokenData;
    NSString *deviceToken;
    if (@available(iOS 13, *)) {
        deviceToken = [self hexadecimalStringFromData:deviceTokenData];
    } else {
        deviceToken = [[[[deviceTokenData description]
            stringByReplacingOccurrencesOfString:@"<"withString:@""]
            stringByReplacingOccurrencesOfString:@">" withString:@""]
            stringByReplacingOccurrencesOfString:@" " withString:@""];
    }
    apnsToken = deviceToken;
    DDLogDebug(@"Device APNS Token: %@", deviceToken);
    if (@available(iOS 10, *)) {
        return;
    }
    [FCMPluginIOS9Support application:application didRegisterForRemoteNotificationsWithDeviceToken:deviceTokenData];
}

- (void)application:(UIApplication *)application didFailToRegisterForRemoteNotifications:(NSError *)error {
    DDLogDebug(@"Failed to register for remote notifications: %@", error);
    if (@available(iOS 10, *)) {
        return;
    }
    [FCMPluginIOS9Support application:application didFailToRegisterForRemoteNotifications:error];
}

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-implementations"
- (void)application:(UIApplication *)application didReceiveRemoteNotification:(NSDictionary *)userInfo {
    if (@available(iOS 10, *)) {
        return;
    }
    [FCMPluginIOS9Support application:application didReceiveRemoteNotification:userInfo];
}
#pragma clang diagnostic pop

- (void)application:(UIApplication *)application didReceiveRemoteNotification:(NSDictionary *)userInfo
fetchCompletionHandler:(void (^)(UIBackgroundFetchResult))completionHandler {
    [[FIRMessaging messaging] appDidReceiveMessage:userInfo];

    if (@available(iOS 10, *)) {
        // Print message ID.
        DDLogDebug(@"Message ID: %@", userInfo[@"gcm.message_id"]);

        // Print full message.
        DDLogDebug(@"%@", userInfo);

        // Detect data-only push: has our jsonData key AND no aps.alert (a display notification
        // can also carry a jsonData payload, so we must exclude those).
        NSString *jsonDataString = userInfo[@"jsonData"];
        NSDictionary *aps = userInfo[@"aps"];
        BOOL hasDisplayNotification = (aps[@"alert"] != nil);
        BOOL isDataOnlyPush = !hasDisplayNotification &&
                              (jsonDataString != nil && [jsonDataString isKindOfClass:[NSString class]]);

        if (isDataOnlyPush) {
            DDLogDebug(@"Data-only push received");
            NSError *parseError;
            NSData *jsonBytes = [jsonDataString dataUsingEncoding:NSUTF8StringEncoding];
            NSDictionary *parsedData = [NSJSONSerialization JSONObjectWithData:jsonBytes options:0 error:&parseError];

            if (application.applicationState == UIApplicationStateActive) {
                // Foreground — dispatch directly to JS.
                DDLogDebug(@"Data-only push: app in foreground, dispatching to JS");
                NSMutableDictionary *pushData = [userInfo mutableCopy];
                [FCMPlugin dispatchNotification:pushData];
            } else {
                // Background / inactive.
                if (parseError || !parsedData) {
                    // Can't parse jsonData — store raw payload so it's not lost; no banner possible.
                    DDLogDebug(@"Data-only push: failed to parse jsonData, storing raw payload: %@", parseError);
                    NSMutableDictionary *storedPayload = [userInfo mutableCopy];
                    [storedPayload setValue:@(NO) forKey:@"wasTapped"];
                    [AppDelegate storeDataNotification:storedPayload];
                    completionHandler(UIBackgroundFetchResultNewData);
                    return;
                }

                NSString *title = parsedData[@"title"];
                BOOL hasTitle = (title != nil && ![title isEqualToString:@""]);

                if (hasTitle) {
                    // Has a title — show a visible banner, persist for getDeliveredNotifications,
                    // and delete the FCM token if this is a deactivate push.
                    DDLogDebug(@"Data-only push: background with title, showing banner and persisting");
                    // Pre-compute the identifier so the stored payload and the scheduled
                    // notification share the same value — needed for reliable clear-on-tap.
                    NSString *notificationId = parsedData[@"id"] ?: [[NSUUID UUID] UUIDString];
                    NSMutableDictionary *storedPayload = [userInfo mutableCopy];
                    [storedPayload setValue:@(NO) forKey:@"wasTapped"];
                    [storedPayload setValue:notificationId forKey:@"HRSNotificationIdentifier"];
                    [AppDelegate storeDataNotification:storedPayload];
                    [AppDelegate scheduleLocalNotificationForDataPush:userInfo withParsedData:parsedData withIdentifier:notificationId];
                } else {
                    // No title — silent push, dispatch to JS (mirrors Android silent push path).
                    DDLogDebug(@"Data-only push: background with no title, dispatching to JS");
                    NSMutableDictionary *pushData = [userInfo mutableCopy];
                    [FCMPlugin dispatchNotification:pushData];
                }
            }
            completionHandler(UIBackgroundFetchResultNewData);
            return;
        }

        // Standard display notification handling.
        if(application.applicationState == UIApplicationStateBackground) {
            NSMutableDictionary *jsonData = [userInfo mutableCopy];
            [jsonData setValue:@(NO) forKey:@"wasTapped"];
            DDLogDebug(@"app in background");
            lastPush = jsonData;
            [AppDelegate setInitialPushPayload:lastPush];
        } else if(application.applicationState == UIApplicationStateInactive) {
            NSMutableDictionary *jsonData = [userInfo mutableCopy];
            [jsonData setValue:@(YES) forKey:@"wasTapped"];
            DDLogDebug(@"app opened by user tap");
            lastPush = jsonData;
            [AppDelegate setInitialPushPayload:lastPush];
        } else if(application.applicationState == UIApplicationStateActive) {
            NSMutableDictionary *jsonData = [userInfo mutableCopy];
            DDLogDebug(@"app active");
            [FCMPlugin dispatchNotification:jsonData];
        }

        completionHandler(UIBackgroundFetchResultNoData);
        return;
    }

    [FCMPluginIOS9Support application:application didReceiveRemoteNotification:userInfo fetchCompletionHandler:completionHandler];
}
// [END message_handling]

- (void)messaging:(nonnull FIRMessaging *)messaging didReceiveRegistrationToken:(NSString *)deviceToken {
    DDLogDebug(@"Device FCM Token: %@", deviceToken);
    if(deviceToken == nil) {
        fcmToken = nil;
        [FCMPlugin dispatchTokenRefresh:nil];
        return;
    }
    // Notify about received token.
    NSDictionary *dataDict = [NSDictionary dictionaryWithObject:deviceToken forKey:@"token"];
    [[NSNotificationCenter defaultCenter] postNotificationName:@"FCMToken" object:nil userInfo:dataDict];
    fcmToken = deviceToken;
    [FCMPlugin dispatchTokenRefresh:deviceToken];
    [self connectToFcm];
}

// [BEGIN connect_to_fcm]
- (void)connectToFcm {
    // Won't connect since there is no token
    if (!fcmToken) {
        return;
    }
    [[FIRMessaging messaging] subscribeToTopic:@"ios"];
    [[FIRMessaging messaging] subscribeToTopic:@"all"];
}
// [END connect_to_fcm]

- (void)applicationDidBecomeActive:(UIApplication *)application {
    DDLogDebug(@"app become active");
    [self connectToFcm];
}

// [BEGIN disconnect_from_fcm]
- (void)applicationDidEnterBackground:(UIApplication *)application {
    DDLogDebug(@"app entered background");
    DDLogDebug(@"Disconnected from FCM");
}
// [END disconnect_from_fcm]

+ (void)setLastPush:(NSDictionary*)push {
    lastPush = push;
}

+ (void)setInitialPushPayload:(NSDictionary*)payload {
    if(initialPushPayload == nil) {
        initialPushPayload = payload;
    }
}

+ (NSDictionary*)getLastPush {
    NSDictionary* returnValue = lastPush;
    lastPush = nil;
    return returnValue;
}

+ (NSDictionary*)getInitialPushPayload {
    return initialPushPayload;
}

+ (NSString*)getFCMToken {
    return fcmToken;
}

+ (NSString*)getAPNSToken {
    return apnsToken;
}

+ (void)hasPushPermission:(void (^)(NSNumber* yesNoOrNil))block {
    if ([UNUserNotificationCenter class] == nil) {
        [FCMPluginIOS9Support hasPushPermission:block];
        return;
    }
    UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];
    [center getNotificationSettingsWithCompletionHandler:^(UNNotificationSettings *settings){
        switch (settings.authorizationStatus) {
            case UNAuthorizationStatusAuthorized: {
                block([NSNumber numberWithBool:YES]);
            }
            case UNAuthorizationStatusDenied: {
                block([NSNumber numberWithBool:NO]);
            }
            default: {
                block(nil);
            }
        }
    }];
}

- (NSString *)hexadecimalStringFromData:(NSData *)data {
    NSUInteger dataLength = data.length;
    if (dataLength == 0) {
        return nil;
    }

    const unsigned char *dataBuffer = data.bytes;
    NSMutableString *hexString  = [NSMutableString stringWithCapacity:(dataLength * 2)];
    for (int i = 0; i < dataLength; ++i) {
        [hexString appendFormat:@"%02x", dataBuffer[i]];
    }
    return [hexString copy];
}

// Schedule a visible local notification in the system tray for a data-only FCM push.
+ (void)scheduleLocalNotificationForDataPush:(NSDictionary *)userInfo withParsedData:(NSDictionary *)parsedData withIdentifier:(NSString *)notificationId {
    NSString *title = parsedData[@"title"] ?: @"";
    NSString *body  = parsedData[@"body"]  ?: @"";

    UNMutableNotificationContent *content = [[UNMutableNotificationContent alloc] init];
    content.title   = title;
    content.body    = body;
    content.sound   = [UNNotificationSound defaultSound];
    // Embed the original FCM userInfo so it can be recovered when the notification is tapped.
    // Also stamp a flag so FCMNotificationCenterDelegate can distinguish this locally-scheduled
    // data-only notification from a regular display notification (both may carry a jsonData key).
    NSMutableDictionary *enrichedUserInfo = [userInfo mutableCopy];
    enrichedUserInfo[@"HRSIsDataOnlyNotification"] = @YES;
    content.userInfo = enrichedUserInfo;

    // nil trigger = deliver immediately; 1-second interval avoids a framework restriction
    // on triggers with interval < 1.
    UNTimeIntervalNotificationTrigger *trigger = [UNTimeIntervalNotificationTrigger triggerWithTimeInterval:1 repeats:NO];
    UNNotificationRequest *request = [UNNotificationRequest requestWithIdentifier:notificationId content:content trigger:trigger];
    [[UNUserNotificationCenter currentNotificationCenter] addNotificationRequest:request withCompletionHandler:^(NSError *error) {
        if (error) {
            DDLogDebug(@"Data-only notification scheduling error: %@", error);
        } else {
            DDLogDebug(@"Data-only notification scheduled with id: %@", notificationId);
        }
    }];
}

// Persist a data-only notification to NSUserDefaults so it survives app termination.
+ (void)storeDataNotification:(NSDictionary *)notification {
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    NSArray *existing = [defaults arrayForKey:kHRSPendingDataNotificationsKey];
    NSMutableArray *updated = existing ? [existing mutableCopy] : [NSMutableArray array];
    [updated addObject:notification];
    [defaults setObject:[updated copy] forKey:kHRSPendingDataNotificationsKey];
    [defaults synchronize];
    DDLogDebug(@"Stored data-only notification. Total pending: %lu", (unsigned long)updated.count);
}

// Remove a single data-only notification from UserDefaults by its notification identifier (the
// id field from jsonData). Called when the user taps the banner so we don't double-deliver.
+ (void)clearStoredDataNotification:(NSString *)notificationId {
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    NSArray *existing = [defaults arrayForKey:kHRSPendingDataNotificationsKey];
    if (!existing || existing.count == 0) return;

    NSMutableArray *updated = [existing mutableCopy];
    [updated filterUsingPredicate:[NSPredicate predicateWithBlock:^BOOL(id obj, NSDictionary *bindings) {
        NSDictionary *entry = (NSDictionary *)obj;
        NSString *storedIdentifier = entry[@"HRSNotificationIdentifier"];
        return storedIdentifier == nil || ![storedIdentifier isEqualToString:notificationId];
    }]];
    [defaults setObject:[updated copy] forKey:kHRSPendingDataNotificationsKey];
    [defaults synchronize];
    DDLogDebug(@"Cleared tapped data-only notification '%@' from UserDefaults store", notificationId);
}

// Retrieve and clear all persistently stored data-only notifications.
+ (NSArray *)getDeliveredNotifications {
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    NSArray *notifications = [defaults arrayForKey:kHRSPendingDataNotificationsKey];
    [defaults removeObjectForKey:kHRSPendingDataNotificationsKey];
    [defaults synchronize];
    DDLogDebug(@"Retrieved %lu pending data-only notifications", (unsigned long)notifications.count);
    if (!notifications) return @[];
    // Strip internal tracking keys before handing the payloads to JS.
    NSMutableArray *result = [NSMutableArray arrayWithCapacity:notifications.count];
    for (NSDictionary *notification in notifications) {
        NSMutableDictionary *cleaned = [notification mutableCopy];
        [cleaned removeObjectForKey:@"HRSNotificationIdentifier"];
        [result addObject:cleaned];
    }
    return result;
}

// Added deleteInstanceId method in AppDelegate+FCMPlugin.m as it is being consumed in logout.
+ (void)deleteInstanceId:(void (^)(NSError *error))handler {
    if ([self getAPNSToken] == nil) {
        /* Fixed DEV-16505 Re-retrieve the APNS token if it's not available, as it will be needed when the FCM token is refreshed following the deletion of the FCM current instance. */
        UNAuthorizationOptions authOptions = UNAuthorizationOptionAlert | UNAuthorizationOptionSound | UNAuthorizationOptionBadge;
        [[UNUserNotificationCenter currentNotificationCenter] requestAuthorizationWithOptions:authOptions completionHandler:^(BOOL granted, NSError* _Nullable error) {
            if (granted) {
                dispatch_async(dispatch_get_main_queue(), ^{
                    [[UIApplication sharedApplication] registerForRemoteNotifications];
                });
            } else {
                DDLogDebug(@"User Notification permission denied: %@", error.localizedDescription);
            }
        }];
    }
    //Replaced deleteIDWithHandler with deleteDataWithCompletion to delete FCM token.
    [[FIRMessaging messaging] deleteDataWithCompletion:handler];
    //Added deleteWithCompletion method to delete FCM instance on logout.
    [[FIRInstallations installations] deleteWithCompletion:handler];
}
@end

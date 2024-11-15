#import "AppDelegate+FCMPlugin.h"
#import "FCMPlugin.h"
#import "FCMNotificationCenterDelegate.h"
#import <objc/runtime.h>
#import <Foundation/Foundation.h>
#import <CocoaLumberjack/CocoaLumberjack.h>

#define ddLogLevel DDLogLevelAll

@import UserNotifications;

// Implement UNUserNotificationCenterDelegate to receive display notification via APNS for devices
// running iOS 10 and above.
@interface FCMNotificationCenterDelegate () <UNUserNotificationCenterDelegate>

@end

@implementation FCMNotificationCenterDelegate

NSMutableArray<NSObject<UNUserNotificationCenterDelegate>*> *subNotificationCenterDelegates;

- (void) forceNotificationCenterDelegate:(float)timeout {
    [self setNotificationCenterDelegate];
    if(timeout < 0) {
        // The job should be done.
        return;
    }
    SEL thisMethodSelector = NSSelectorFromString(@"forceNotificationCenterDelegate:");
    if([self respondsToSelector:thisMethodSelector]) {
        DDLogDebug(@"FCMNotificationCenterDelegate found: %@", [UNUserNotificationCenter currentNotificationCenter].delegate);
        float remainingTimeout = timeout - 0.1f;
        NSInvocation *invocation = [NSInvocation invocationWithMethodSignature:[self methodSignatureForSelector:thisMethodSelector]];
        [invocation setSelector:thisMethodSelector];
        [invocation setTarget:self];
        [invocation setArgument:&(remainingTimeout) atIndex:2];
        [NSTimer scheduledTimerWithTimeInterval:0.1f invocation:invocation repeats:NO];
        return;
    }
    DDLogDebug(@"forceNotificationCenterDelegate selector not found in FCMNotificationCenterDelegate");
}

- (void)configureForNotifications {
    DDLogDebug(@"FCMNotificationCenterDelegate.configureForNotifications called");
    subNotificationCenterDelegates = [[NSMutableArray alloc]initWithCapacity:0];
    [self setNotificationCenterDelegate];
    [self initializeRecentNotifications];
    [self forceNotificationCenterDelegate:10];
}

- (void) setNotificationCenterDelegate {
    if([UNUserNotificationCenter currentNotificationCenter].delegate == self) {
        return;
    }
    if([UNUserNotificationCenter currentNotificationCenter].delegate != nil) {
        [subNotificationCenterDelegates addObject:[UNUserNotificationCenter currentNotificationCenter].delegate];
    }
    [UNUserNotificationCenter currentNotificationCenter].delegate = self;
}

NSMutableDictionary<NSString *, NSNumber *> *recentNotifications;
NSTimeInterval discardThreshold ;

- (void)initializeRecentNotifications {
    DDLogDebug(@"FCMNotificationCenterDelegate.initializeRecentNotifications called");
    // Initialize the mutable dictionary here
    recentNotifications = [NSMutableDictionary dictionary];
    discardThreshold = 60.0;
}

// Handle incoming notification messages while app is in the foreground.
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
       willPresentNotification:(UNNotification *)notification
         withCompletionHandler:(void (^)(UNNotificationPresentationOptions))completionHandler {
    DDLogDebug(@"FCMNotificationCenterDelegate.willPresentNotification!");
    // iOS 18 is presenting the notification twice thus handled https://forums.developer.apple.com/forums/thread/761597
    if(@available(iOS 18, *)) {
        DDLogDebug(@"iOS 18 handling for notification");
        NSTimeInterval now = [[NSDate date] timeIntervalSince1970];
        // Check if the notification ID already exists and is within the discard threshold
        NSNumber *lastTimestamp = recentNotifications[notification.request.identifier];
        if (lastTimestamp && (now - [lastTimestamp doubleValue] < discardThreshold)) {
            NSLog(@"Discarding duplicate notification with id: %@", notification.request.identifier);
            return;
        }
        NSLog(@"Processing notification:"); // Process the notification and update timestamp
        recentNotifications[notification.request.identifier] = @([[NSDate date] timeIntervalSince1970]);
        NSLog(@"Will present a new notification: ");
        NSDictionary *jsonData = [self extractJSONData:notification withWasTapped:NO];
        [FCMPlugin dispatchNotification:jsonData];
        __block UNNotificationPresentationOptions notificationPresentationOptions = UNNotificationPresentationOptionNone;
        completionHandler(notificationPresentationOptions);
        [self cleanUpOldEntries];
    } else { // For other OS which dont have the issue https://forums.developer.apple.com/forums/thread/761597
        DDLogDebug(@"Not iOS 18, will present the notification");
        NSDictionary *jsonData = [self extractJSONData:notification withWasTapped:NO];
        [FCMPlugin dispatchNotification:jsonData];
        __block UNNotificationPresentationOptions notificationPresentationOptions = UNNotificationPresentationOptionNone;
        completionHandler(notificationPresentationOptions);
    }
}

- (void)cleanUpOldEntries {
    NSTimeInterval now = [[NSDate date] timeIntervalSince1970];
    NSMutableArray *keysToDelete = [NSMutableArray array];

    // Iterate through the dictionary and find entries older than the threshold
    for (NSString *notificationId in recentNotifications) {
        NSNumber *timestamp = recentNotifications[notificationId];
        if (now - [timestamp doubleValue] > discardThreshold) {
            DDLogDebug(@"DELETING OLD ENTRY %@" , notificationId);
            [keysToDelete addObject:notificationId];
        }
    }
    if(keysToDelete){    // Remove old entries
        [recentNotifications removeObjectsForKeys:keysToDelete];
    }
}

// Handle notification messages after display notification is tapped by the user.
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
didReceiveNotificationResponse:(UNNotificationResponse *)response
         withCompletionHandler:(void (^)(void))completionHandler {
    DDLogDebug(@"FCMNotificationCenterDelegate.didReceiveNotificationResponse!");
    NSDictionary* jsonData = [self extractJSONData:response.notification withWasTapped:YES];
    [AppDelegate setInitialPushPayload:jsonData];
    [FCMPlugin dispatchNotification:jsonData];
    completionHandler();
}

- (NSDictionary*)extractJSONData:(UNNotification*)notification
             withWasTapped:(BOOL)wasTapped {
    UNNotificationContent *content = notification.request.content;
    DDLogDebug(@"Push notification received: title=\"%@\" subtitle=\"%@\" body=\"%@\" badge=\"%@\"",
          content.title, content.subtitle, content.body, content.badge);
    DDLogDebug(@"Push data received: %@", content.userInfo);
    NSMutableDictionary *notificationData = [content.userInfo mutableCopy];
    if([notificationData objectForKey:@"wasTapped"] == nil) { [notificationData setValue:@(wasTapped) forKey:@"wasTapped"]; }
    if([notificationData objectForKey:@"title"] == nil) { [notificationData setValue:content.title forKey:@"title"]; }
    if([notificationData objectForKey:@"subtitle"] == nil) { [notificationData setValue:content.subtitle forKey:@"subtitle"]; }
    if([notificationData objectForKey:@"body"] == nil) { [notificationData setValue:content.body forKey:@"body"]; }
    if([notificationData objectForKey:@"badge"] == nil) { [notificationData setValue:content.badge forKey:@"badge"]; }
    return notificationData;
}

@end

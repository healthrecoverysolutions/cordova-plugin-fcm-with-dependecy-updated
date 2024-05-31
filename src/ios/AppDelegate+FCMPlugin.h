#import "AppDelegate.h"
#import <UIKit/UIKit.h>
#import <Cordova/CDVViewController.h>

@interface AppDelegate (FCMPlugin)

+ (NSDictionary*)getLastPush;
+ (NSDictionary*)getInitialPushPayload;
+ (NSString*)getFCMToken;
+ (NSString*)getAPNSToken;
+ (void)deleteInstanceId:(void (^)(NSError *error))handler;
+ (void)setLastPush:(NSDictionary*)push;
+ (void)setInitialPushPayload:(NSDictionary*)payload;
+ (void)requestPushPermission:(void (^)(BOOL yesOrNo, NSError* error))block withOptions:(NSDictionary*)options;
+ (void)hasPushPermission:(void (^)(NSNumber* yesNoOrNil))block;

@end

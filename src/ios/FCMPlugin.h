#import <UIKit/UIKit.h>
#import <Cordova/CDVPlugin.h>

@interface FCMPlugin : CDVPlugin {}

+ (void)dispatchTokenRefresh:(NSString *)token;
+ (void)dispatchNotification:(NSDictionary *)notification;
+ (NSString *)toJsonString:(NSDictionary *)dictionary;

- (void)notifyFCMTokenRefresh:(NSString*) token;
- (void)hasPermission:(CDVInvokedUrlCommand*)command;
- (void)getToken:(CDVInvokedUrlCommand*)command;
- (void)returnTokenOrRetry:(void (^)(NSString* fcmToken))onSuccess;
- (void)getAPNSToken:(CDVInvokedUrlCommand*)command;
- (void)getInitialPushPayload:(CDVInvokedUrlCommand*)command;
- (void)deleteInstanceId:(CDVInvokedUrlCommand*)command;
- (void)clearAllNotifications:(CDVInvokedUrlCommand *)command;
- (void)subscribeToTopic:(CDVInvokedUrlCommand*)command;
- (void)unsubscribeFromTopic:(CDVInvokedUrlCommand*)command;
- (void)notifyOfMessage:(NSDictionary*) payload;
- (void)appEnterBackground;
- (void)appEnterForeground;

@end

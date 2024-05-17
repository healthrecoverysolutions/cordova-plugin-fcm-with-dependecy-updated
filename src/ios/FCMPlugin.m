#include <sys/types.h>
#include <sys/sysctl.h>
#import "AppDelegate+FCMPlugin.h"
#import <UserNotifications/UserNotifications.h>
#import <Cordova/CDV.h>
#import <WebKit/WebKit.h>
#import "FCMPlugin.h"
#import <Firebase.h>
#import <CocoaLumberjack/CocoaLumberjack.h>

#define ddLogLevel DDLogLevelAll

@interface FCMPlugin () {}
@end

@implementation FCMPlugin

static BOOL appInForeground = YES;

static NSString *EVENT_TYPE_NOTIFICATION = @"notification";
static NSString *EVENT_TYPE_TOKEN_REFRESH = @"tokenRefresh";
static NSString *jsEventBridgeCallbackId = nil;
static FCMPlugin *fcmPluginInstance = nil;

+ (void)dispatchTokenRefresh:(NSString *)token {
    if (fcmPluginInstance != nil) {
        [fcmPluginInstance notifyFCMTokenRefresh:token];
    } else {
        DDLogWarn(@"dispatchTokenRefresh plugin instance not set");
    }
}

+ (void)dispatchNotification:(NSDictionary *)notification {
    if (fcmPluginInstance != nil) {
        [fcmPluginInstance notifyOfMessage:notification];
    } else {
        DDLogWarn(@"dispatchNotification plugin instance not set");
    }
}

+ (NSString *)toJsonString:(NSDictionary *)dictionary {
    int NO_FORMATTING = 0;
    NSError *error;
    NSData *jsonData = [NSJSONSerialization dataWithJSONObject:dictionary options:NO_FORMATTING error:&error];
    
    if (jsonData) {
        return [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
    }
    
    DDLogWarn(@"toJsonString error: %@", error);
    return nil;
}

- (void)pluginInitialize {
    DDLogDebug(@"FCM plugin ready");
    fcmPluginInstance = self;
     [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onPause) name:UIApplicationDidEnterBackgroundNotification object:nil];
     [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onResume) name:UIApplicationWillEnterForegroundNotification object:nil];
}

- (void)onResume {
    [self appEnterForeground];
}

- (void)onPause {
    [self appEnterBackground];
}

- (void)onAppTerminate {
    DDLogDebug(@"FCM plugin terminate");
    [[NSNotificationCenter defaultCenter] removeObserver:self];
    fcmPluginInstance = nil;
}

- (void)hasPermission:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        [AppDelegate hasPushPermission:^(NSNumber* pushPermission){
            __block CDVPluginResult *commandResult;
            if (pushPermission == nil) {
                DDLogDebug(@"has push permission: unknown");
                commandResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
            } else if ([pushPermission boolValue] == YES) {
                DDLogDebug(@"has push permission: true");
                commandResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:YES];
            } else if ([pushPermission boolValue] == NO) {
                DDLogDebug(@"has push permission: false");
                commandResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:NO];
            }
            [self.commandDelegate sendPluginResult:commandResult callbackId:command.callbackId];
        }];
    }];
}

- (void)setSharedEventDelegate:(CDVInvokedUrlCommand *)command {
    jsEventBridgeCallbackId = command.callbackId;
    DDLogDebug(@"setSharedEventDelegate() %@", jsEventBridgeCallbackId);
}

- (void)getToken:(CDVInvokedUrlCommand *)command {
    DDLogDebug(@"getToken()");
    [self returnTokenOrRetry:^(NSString* fcmToken){
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:fcmToken];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)returnTokenOrRetry:(void (^)(NSString* fcmToken))onSuccess {
    NSString* fcmToken = [AppDelegate getFCMToken];
    if(fcmToken != nil) {
        onSuccess(fcmToken);
        return;
    }
    SEL thisMethodSelector = NSSelectorFromString(@"returnTokenOrRetry:");
    DDLogDebug(@"FCMToken unavailable, it'll retry in one second");
    NSInvocation *invocation = [NSInvocation invocationWithMethodSignature:[self methodSignatureForSelector:thisMethodSelector]];
    [invocation setSelector:thisMethodSelector];
    [invocation setTarget:self];
    [invocation setArgument:&(onSuccess) atIndex:2]; //arguments 0 and 1 are self and _cmd respectively, automatically set by NSInvocationion
    [NSTimer scheduledTimerWithTimeInterval:1 invocation:invocation repeats:NO];
}

- (void)getAPNSToken:(CDVInvokedUrlCommand *)command  {
    DDLogDebug(@"get APNS Token");
    [self.commandDelegate runInBackground:^{
        CDVPluginResult* pluginResult = nil;
        NSString* apnsToken = [AppDelegate getAPNSToken];
        DDLogDebug(@"get APNS Token value: %@", apnsToken);
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:apnsToken];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)clearAllNotifications:(CDVInvokedUrlCommand *)command {
  [self.commandDelegate runInBackground:^{
    DDLogDebug(@"clear all notifications");
    [[UIApplication sharedApplication] setApplicationIconBadgeNumber:1];
    [[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
  }];
}

- (void)subscribeToTopic:(CDVInvokedUrlCommand *)command {
    NSString* topic = [command.arguments objectAtIndex:0];
    DDLogDebug(@"subscribe To Topic %@", topic);
    [self.commandDelegate runInBackground:^{
        if(topic != nil)[[FIRMessaging messaging] subscribeToTopic:topic];
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:topic];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)unsubscribeFromTopic:(CDVInvokedUrlCommand *)command {
    NSString* topic = [command.arguments objectAtIndex:0];
    DDLogDebug(@"unsubscribe From Topic %@", topic);
    [self.commandDelegate runInBackground:^{
        if(topic != nil)[[FIRMessaging messaging] unsubscribeFromTopic:topic];
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:topic];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)requestPushPermission:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        NSNumber* ios9SupportTimeout = [command argumentAtIndex:0 withDefault:[NSNumber numberWithFloat:10]];
        NSNumber* ios9SupportInterval = [command argumentAtIndex:1 withDefault:[NSNumber numberWithFloat:0.3]];
        DDLogDebug(@"requestPushPermission { ios9SupportTimeout:%@ ios9SupportInterval:%@ }", ios9SupportTimeout, ios9SupportInterval);
        id objects[] = { ios9SupportTimeout, ios9SupportInterval };
        id keys[] = { @"ios9SupportTimeout", @"ios9SupportInterval" };
        NSDictionary* options = [NSDictionary dictionaryWithObjects:objects forKeys:keys count:2];
        [AppDelegate requestPushPermission:^(BOOL pushPermission, NSError* _Nullable error) {
            if(error != nil){
                DDLogDebug(@"push permission request error: %@", error);
                __block CDVPluginResult *commandResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[error description]];
                [self.commandDelegate sendPluginResult:commandResult callbackId:command.callbackId];
                return;
            }
            DDLogDebug(@"push permission request result: %@", pushPermission ? @"Yes" : @"No");
            __block CDVPluginResult *commandResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:pushPermission];
            [self.commandDelegate sendPluginResult:commandResult callbackId:command.callbackId];
        } withOptions:options];
    }];
}

- (void)getInitialPushPayload:(CDVInvokedUrlCommand *)command {
    DDLogDebug(@"getInitialPushPayload");
    [self.commandDelegate runInBackground:^{
        NSDictionary* jsonData = [AppDelegate getInitialPushPayload];
        if (jsonData == nil) {
            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:nil];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            return;
        }
        DDLogDebug(@"getInitialPushPayload value: %@", [FCMPlugin toJsonString:jsonData]);
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:jsonData];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)deleteInstanceId:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        [AppDelegate deleteInstanceId:^(NSError *error) {
            __block CDVPluginResult *commandResult;
            if(error == nil) {
                DDLogDebug(@"InstanceID deleted");
                commandResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
            } else {
                DDLogDebug(@"InstanceID deletion error: %@", error);
                commandResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[error description]];
            }
            [self.commandDelegate sendPluginResult:commandResult callbackId:command.callbackId];
        }];
    }];
}

- (void)notifyOfMessage:(NSDictionary *)payload {
    DDLogDebug(@"notifyOfMessage payload: %@", [FCMPlugin toJsonString:payload]);
    [self dispatchJSEvent:EVENT_TYPE_NOTIFICATION withData:payload];
}

- (void)notifyFCMTokenRefresh:(NSString *)token {
    DDLogDebug(@"notifyFCMTokenRefresh token: %@", token);
    NSMutableDictionary* eventData = [[NSMutableDictionary alloc] init];
    [eventData setValue:token forKey:@"token"];
    [self dispatchJSEvent:EVENT_TYPE_TOKEN_REFRESH withData:eventData];
}

- (void)dispatchJSEvent:(NSString *)eventName withData:(NSDictionary *)jsData {
    if(jsEventBridgeCallbackId == nil) {
        DDLogDebug(@"dispatchJSEvent: Unable to send event due to unreachable bridge context: %@ with %@", eventName, [FCMPlugin toJsonString:jsData]);
        return;
    }
    DDLogDebug(@"dispatchJSEvent: %@ with %@", eventName, [FCMPlugin toJsonString:jsData]);
    NSMutableDictionary* eventPayload = [[NSMutableDictionary alloc] init];
    [eventPayload setValue:eventName forKey:@"type"];
    [eventPayload setValue:jsData forKey:@"data"];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:eventPayload];
    [pluginResult setKeepCallbackAsBool:TRUE];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:jsEventBridgeCallbackId];
}

- (void)appEnterBackground {
    DDLogDebug(@"Set state background");
    appInForeground = NO;
}

- (void)appEnterForeground {
    DDLogDebug(@"Set state foreground");
    NSDictionary* lastPush = [AppDelegate getLastPush];
    if (lastPush != nil) {
        [self notifyOfMessage:lastPush];
    }
    appInForeground = YES;
}

@end

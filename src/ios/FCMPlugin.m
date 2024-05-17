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

static NSString *notificationEventName = @"notification";
static NSString *tokenRefreshCallback = @"tokenRefresh";
static NSString *jsEventBridgeCallbackId;
static FCMPlugin *fcmPluginInstance;

+ (FCMPlugin *)fcmPlugin {
    return fcmPluginInstance;
}

- (void)ready:(CDVInvokedUrlCommand *)command {
    DDLogDebug(@"Cordova view ready");
    fcmPluginInstance = self;
    [self.commandDelegate runInBackground:^{
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
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
    DDLogDebug(@"start Js Event Bridge");
    jsEventBridgeCallbackId = command.callbackId;
}

- (void)getToken:(CDVInvokedUrlCommand *)command {
    DDLogDebug(@"get Token");
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
        NSData* dataPayload = [AppDelegate getInitialPushPayload];
        if (dataPayload == nil) {
            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:nil];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            return;
        }
        NSString *strUTF8 = [[NSString alloc] initWithData:dataPayload encoding:NSUTF8StringEncoding];
        NSData *dataPayloadUTF8 = [strUTF8 dataUsingEncoding:NSUTF8StringEncoding];
        NSError* error = nil;
        NSDictionary *payloadDictionary = [NSJSONSerialization JSONObjectWithData:dataPayloadUTF8 options:0 error:&error];
        if (error) {
            NSString* errorMessage = [NSString stringWithFormat:@"%@ => '%@'", [error localizedDescription], strUTF8];
            DDLogDebug(@"getInitialPushPayload error: %@", errorMessage);
            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_JSON_EXCEPTION messageAsString:errorMessage];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            return;
        }
        DDLogDebug(@"getInitialPushPayload value: %@", payloadDictionary);
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:payloadDictionary];
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

- (void)notifyOfMessage:(NSData *)payload {
    DDLogDebug(@"notifyOfMessage payload: %@", payload);
    NSString* JSONString = [[NSString alloc] initWithBytes:[payload bytes] length:[payload length] encoding:NSUTF8StringEncoding];
    [self dispatchJSEvent:notificationEventName withData:JSONString];
}

- (void)notifyFCMTokenRefresh:(NSString *)token {
    DDLogDebug(@"notifyFCMTokenRefresh token: %@", token);
    NSString* jsToken = [NSString stringWithFormat:@"\"%@\"", token];
    [self dispatchJSEvent:tokenRefreshCallback withData:jsToken];
}

- (void)dispatchJSEvent:(NSString *)eventName withData:(NSString *)jsData {
    if(jsEventBridgeCallbackId == nil) {
        DDLogDebug(@"dispatchJSEvent: Unable to send event due to unreachable bridge context: %@ with %@", eventName, jsData);
        return;
    }
    DDLogDebug(@"dispatchJSEvent: %@ with %@", eventName, jsData);
    NSString* eventDataTemplate = @"[\"%@\",%@]";
    NSString* eventData = [NSString stringWithFormat:eventDataTemplate, eventName, jsData];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:eventData];
    [pluginResult setKeepCallbackAsBool:TRUE];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:jsEventBridgeCallbackId];
}

- (void)appEnterBackground {
    DDLogDebug(@"Set state background");
    appInForeground = NO;
}

- (void)appEnterForeground {
    DDLogDebug(@"Set state foreground");
    NSData* lastPush = [AppDelegate getLastPush];
    if (lastPush != nil) {
        [FCMPlugin.fcmPlugin notifyOfMessage:lastPush];
    }
    appInForeground = YES;
}

@end

#import <UIKit/UIKit.h>

@interface FCMNotificationCenterDelegate : NSObject {}

- (void)configureForNotifications;
- (void)cleanUpOldEntries;
- (void)initializeRecentNotifications;

@end

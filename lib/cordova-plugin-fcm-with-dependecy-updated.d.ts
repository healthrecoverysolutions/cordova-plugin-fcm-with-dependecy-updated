type ErrorCallback = (error: any) => void;
export declare enum FirebaseMessagingEventType {
    NOTIFICATION = "notification",
    TOKEN_REFRESH = "tokenRefresh"
}
export interface IChannelConfiguration {
    /**
     * Channel id, used in the android_channel_id push payload key
     */
    id: string;
    /**
     * Channel name, visible for the user
     */
    name: string;
    /**
     * Channel description, visible for the user
     */
    description?: string;
    /**
     * Importance for notifications of this channel
     * https://developer.android.com/guide/topics/ui/notifiers/notifications#importance
     */
    importance?: 'none' | 'min' | 'low' | 'default' | 'high';
    /**
     * Visibility for notifications of this channel
     * https://developer.android.com/training/notify-user/build-notification#lockscreenNotification
     */
    visibility?: 'public' | 'private' | 'secret';
    /**
     * Default sound resource for notifications of this channel
     * The file should located as resources/raw/[resource name].mp3
     */
    sound?: string;
    /**
     * Enable lights for notifications of this channel
     */
    lights?: boolean;
    /**
     * Enable vibration for notifications of this channel
     */
    vibration?: boolean;
}
export interface INotificationPayload {
    /**
     * Determines whether the notification was tapped or not
     */
    wasTapped: boolean;
    /**
     * FCM notification data hash item
     */
    [others: string]: any;
}
export interface ITokenPayload {
    token: string;
}
export interface IRequestPushPermissionOptions {
    /**
     * Options exclusive for iOS 9 support
     */
    ios9Support?: {
        /**
         * How long it will wait for a decision from the user before returning `false`
         *
         * @default 10
         */
        timeout?: number;
        /**
         * How long between each permission verification
         *
         * @default 0.3
         */
        interval?: number;
    };
}
export interface FirebaseMessagingEvent {
    type: FirebaseMessagingEventType;
    data: INotificationPayload | ITokenPayload;
}
export type FirebaseMessagingEventCallback = (event: FirebaseMessagingEvent) => void;
export declare class FirebaseMessagingCordovaInterface {
    constructor();
    private platformIs;
    setSharedEventDelegate(callback: FirebaseMessagingEventCallback, error: ErrorCallback): void;
    /**
     * Removes existing push notifications from the notifications center
     *
     * @returns {Promise<void>} Async call to native implementation
     */
    clearAllNotifications(): Promise<void>;
    /**
     * For Android, some notification properties are only defined programmatically.
     * Channel can define the default behavior for notifications on Android 8.0+.
     * Once a channel is created, it stays unchangeable until the user uninstalls the app.
     *
     * @param {IChannelConfiguration} channelConfig The parmeters of the new channel
     *
     * @returns {Promise<void>} Async call to native implementation
     */
    createNotificationChannel(channelConfig: IChannelConfiguration): Promise<void>;
    /**
     * This method deletes the InstanceId, revoking all tokens.
     *
     * @returns {Promise<void>} Async call to native implementation
     */
    deleteInstanceId(): Promise<void>;
    /**
     * Gets ios device's current APNS token
     *
     * @returns {Promise<string>} Returns a Promise that resolves with the APNS token
     */
    getAPNSToken(): Promise<string>;
    /**
     * Retrieves the message that, on tap, opened the app
     *
     * @private
     *
     * @returns {Promise<INotificationPayload | null>} Async call to native implementation
     */
    getInitialPushPayload(): Promise<INotificationPayload | null>;
    /**
     * Gets device's current registration id
     *
     * @returns {Promise<string>} Returns a Promise that resolves with the registration id token
     */
    getToken(): Promise<string>;
    /**
     * Checking for permissions.
     *
     * @returns {Promise<boolean | null>} Returns a Promise of:
     * - true: push was allowed (or platform is android)
     * - false: push will not be available
     * - null: still not answered, recommended checking again later.
     */
    hasPermission(): Promise<boolean>;
    /**
     * Request push notification permission, alerting the user if it not have yet decided
     *
     * @param {IRequestPushPermissionOptions} options Options for push request
     * @returns {Promise<boolean>} Returns a Promise that resolves with the permission status
     */
    requestPushPermission(options?: IRequestPushPermissionOptions): Promise<boolean>;
    /**
     * Subscribes you to a [topic](https://firebase.google.com/docs/notifications/android/console-topics)
     *
     * @param {string} topic Topic to be subscribed to
     *
     * @returns {Promise<void>} Async call to native implementation
     */
    subscribeToTopic(topic: string): Promise<void>;
    /**
     * Unsubscribes you from a [topic](https://firebase.google.com/docs/notifications/android/console-topics)
     *
     * @param {string} topic Topic to be unsubscribed from
     *
     * @returns {Promise<void>} Async call to native implementation
     */
    unsubscribeFromTopic(topic: string): Promise<void>;
    initDifferentAccount(accountInfo: any): Promise<void>;
}
export declare const FirebaseMessaging: FirebaseMessagingCordovaInterface;
export {};

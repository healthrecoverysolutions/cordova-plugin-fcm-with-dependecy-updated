////////////////////////////////////////////////////////////////
// Generic Cordova Utilities
////////////////////////////////////////////////////////////////

type SuccessCallback<TValue> = (value: TValue) => void;
type ErrorCallback = (error: any) => void;

function noop() {
    return;
}

function cordovaExec<T>(
    plugin: string,
	method: string,
	successCallback: SuccessCallback<T> = noop,
	errorCallback: ErrorCallback = noop,
	args: any[] = [],
): void {
    if (window.cordova) {
        window.cordova.exec(successCallback, errorCallback, plugin, method, args);

    } else {
        console.warn(`${plugin}.${method}(...) :: cordova not available`);
        errorCallback && errorCallback(`cordova_not_available`);
    }
}

function cordovaExecPromise<T>(plugin: string, method: string, args?: any[]): Promise<T> {
    return new Promise<T>((resolve, reject) => {
        cordovaExec<T>(plugin, method, resolve, reject, args);
    });
}

////////////////////////////////////////////////////////////////
// Plugin Interface
////////////////////////////////////////////////////////////////

const PLUGIN_NAME = 'FCMPlugin';

enum PlatformType {
    ANDROID = 'android',
    IOS = 'ios'
}

export enum FirebaseMessagingEventType {
    NOTIFICATION = 'notification',
    TOKEN_REFRESH = 'tokenRefresh'
}

export interface IChannelConfiguration {
    /**
     * Channel id, used in the android_channel_id push payload key
     */
    id: string
    /**
     * Channel name, visible for the user
     */
    name: string
    /**
     * Channel description, visible for the user
     */
    description?: string
    /**
     * Importance for notifications of this channel
     * https://developer.android.com/guide/topics/ui/notifiers/notifications#importance
     */
    importance?: 'none' | 'min' | 'low' | 'default' | 'high'
    /**
     * Visibility for notifications of this channel
     * https://developer.android.com/training/notify-user/build-notification#lockscreenNotification
     */
    visibility?: 'public' | 'private' | 'secret'
    /**
     * Default sound resource for notifications of this channel
     * The file should located as resources/raw/[resource name].mp3
     */
    sound?: string
    /**
     * Enable lights for notifications of this channel
     */
    lights?: boolean
    /**
     * Enable vibration for notifications of this channel
     */
    vibration?: boolean
}

export interface INotificationPayload {
    /**
     * Determines whether the notification was tapped or not
     */
    wasTapped: boolean
    /**
     * FCM notification data hash item
     */
    [others: string]: any
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
        timeout?: number

        /**
         * How long between each permission verification
         *
         * @default 0.3
         */
        interval?: number
    }
}

export interface FirebaseMessagingEvent {
    type: FirebaseMessagingEventType;
    data: INotificationPayload | ITokenPayload;
}

export type FirebaseMessagingEventCallback = (event: FirebaseMessagingEvent) => void;

function invoke<T>(method: string, ...args: any[]): Promise<T> {
    return cordovaExecPromise<T>(PLUGIN_NAME, method, args);
}

export class FirebaseMessagingCordovaInterface {

    constructor() {
    }

    private platformIs(type: PlatformType): boolean {
        return window.cordova?.platformId === type;   
    }

    public setSharedEventDelegate(callback: FirebaseMessagingEventCallback, error: ErrorCallback): void {
        cordovaExec(PLUGIN_NAME, 'setSharedEventDelegate', callback, error, []);
    }

    /**
     * Removes existing push notifications from the notifications center
     *
     * @returns {Promise<void>} Async call to native implementation
     */
    public clearAllNotifications(): Promise<void> {
        return invoke('clearAllNotifications');
    }

    /**
     * For Android, some notification properties are only defined programmatically.
     * Channel can define the default behavior for notifications on Android 8.0+.
     * Once a channel is created, it stays unchangeable until the user uninstalls the app.
     *
     * @param {IChannelConfiguration} channelConfig The parmeters of the new channel
     *
     * @returns {Promise<void>} Async call to native implementation
     */
    public createNotificationChannel(channelConfig: IChannelConfiguration): Promise<void> {
        if (this.platformIs(PlatformType.ANDROID)) {
            return invoke('createNotificationChannel', channelConfig);
        }
        return Promise.resolve();
    }

    /**
     * This method deletes the InstanceId, revoking all tokens.
     *
     * @returns {Promise<void>} Async call to native implementation
     */
    public deleteInstanceId(): Promise<void> {
        return invoke('deleteInstanceId');
    }

    /**
     * Gets ios device's current APNS token
     *
     * @returns {Promise<string>} Returns a Promise that resolves with the APNS token
     */
    public getAPNSToken(): Promise<string> {
        if (this.platformIs(PlatformType.IOS)) {
            return invoke('getAPNSToken');
        }
        return Promise.resolve('');
    }

    /**
     * Retrieves the message that, on tap, opened the app
     *
     * @private
     *
     * @returns {Promise<INotificationPayload | null>} Async call to native implementation
     */
    public getInitialPushPayload(): Promise<INotificationPayload | null> {
        return invoke('getInitialPushPayload');
    }

    /**
     * Gets device's current registration id
     *
     * @returns {Promise<string>} Returns a Promise that resolves with the registration id token
     */
    public getToken(): Promise<string> {
        return invoke('getToken');
    }

    /**
     * Checking for permissions.
     *
     * @returns {Promise<boolean | null>} Returns a Promise of:
     * - true: push was allowed (or platform is android)
     * - false: push will not be available
     * - null: still not answered, recommended checking again later.
     */
    public hasPermission(): Promise<boolean> {
        if (this.platformIs(PlatformType.IOS)) {
            return invoke('hasPermission');
        }
        return invoke('hasPermission').then((value) => !!value);
    }

    /**
     * Request push notification permission, alerting the user if it not have yet decided
     *
     * @param {IRequestPushPermissionOptions} options Options for push request
     * @returns {Promise<boolean>} Returns a Promise that resolves with the permission status
     */
    public requestPushPermission(options?: IRequestPushPermissionOptions): Promise<boolean> {
        if (this.platformIs(PlatformType.IOS)) {
            const ios9SupportTimeout = options?.ios9Support?.timeout ?? 10;
            const ios9SupportInterval = options?.ios9Support?.interval ?? 0.3;
            return invoke('requestPushPermission', ios9SupportTimeout, ios9SupportInterval);
        }
        return Promise.resolve(true);
    }

    /**
     * Subscribes you to a [topic](https://firebase.google.com/docs/notifications/android/console-topics)
     *
     * @param {string} topic Topic to be subscribed to
     *
     * @returns {Promise<void>} Async call to native implementation
     */
    public subscribeToTopic(topic: string): Promise<void> {
        return invoke('subscribeToTopic', topic);
    }

    /**
     * Unsubscribes you from a [topic](https://firebase.google.com/docs/notifications/android/console-topics)
     *
     * @param {string} topic Topic to be unsubscribed from
     *
     * @returns {Promise<void>} Async call to native implementation
     */
    public unsubscribeFromTopic(topic: string): Promise<void> {
        return invoke('unsubscribeFromTopic', topic);
    }

    public initDifferentAccount(accountInfo: any): Promise<void> {
        return invoke('initDifferentAccount', accountInfo);
    }
}

export const FirebaseMessaging = new FirebaseMessagingCordovaInterface();

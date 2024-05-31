"use strict";
////////////////////////////////////////////////////////////////
// Generic Cordova Utilities
////////////////////////////////////////////////////////////////
Object.defineProperty(exports, "__esModule", { value: true });
exports.FirebaseMessaging = exports.FirebaseMessagingCordovaInterface = exports.FirebaseMessagingEventType = void 0;
function noop() {
    return;
}
function cordovaExec(plugin, method, successCallback, errorCallback, args) {
    if (successCallback === void 0) { successCallback = noop; }
    if (errorCallback === void 0) { errorCallback = noop; }
    if (args === void 0) { args = []; }
    if (window.cordova) {
        window.cordova.exec(successCallback, errorCallback, plugin, method, args);
    }
    else {
        console.warn("".concat(plugin, ".").concat(method, "(...) :: cordova not available"));
        errorCallback && errorCallback("cordova_not_available");
    }
}
function cordovaExecPromise(plugin, method, args) {
    return new Promise(function (resolve, reject) {
        cordovaExec(plugin, method, resolve, reject, args);
    });
}
////////////////////////////////////////////////////////////////
// Plugin Interface
////////////////////////////////////////////////////////////////
var PLUGIN_NAME = 'FCMPlugin';
var PlatformType;
(function (PlatformType) {
    PlatformType["ANDROID"] = "android";
    PlatformType["IOS"] = "ios";
})(PlatformType || (PlatformType = {}));
var FirebaseMessagingEventType;
(function (FirebaseMessagingEventType) {
    FirebaseMessagingEventType["NOTIFICATION"] = "notification";
    FirebaseMessagingEventType["TOKEN_REFRESH"] = "tokenRefresh";
})(FirebaseMessagingEventType || (exports.FirebaseMessagingEventType = FirebaseMessagingEventType = {}));
function invoke(method) {
    var args = [];
    for (var _i = 1; _i < arguments.length; _i++) {
        args[_i - 1] = arguments[_i];
    }
    return cordovaExecPromise(PLUGIN_NAME, method, args);
}
var FirebaseMessagingCordovaInterface = /** @class */ (function () {
    function FirebaseMessagingCordovaInterface() {
    }
    FirebaseMessagingCordovaInterface.prototype.platformIs = function (type) {
        var _a;
        return ((_a = window.cordova) === null || _a === void 0 ? void 0 : _a.platformId) === type;
    };
    FirebaseMessagingCordovaInterface.prototype.setSharedEventDelegate = function (callback, error) {
        cordovaExec(PLUGIN_NAME, 'setSharedEventDelegate', callback, error, []);
    };
    /**
     * Removes existing push notifications from the notifications center
     *
     * @returns {Promise<void>} Async call to native implementation
     */
    FirebaseMessagingCordovaInterface.prototype.clearAllNotifications = function () {
        return invoke('clearAllNotifications');
    };
    /**
     * For Android, some notification properties are only defined programmatically.
     * Channel can define the default behavior for notifications on Android 8.0+.
     * Once a channel is created, it stays unchangeable until the user uninstalls the app.
     *
     * @param {IChannelConfiguration} channelConfig The parmeters of the new channel
     *
     * @returns {Promise<void>} Async call to native implementation
     */
    FirebaseMessagingCordovaInterface.prototype.createNotificationChannel = function (channelConfig) {
        if (this.platformIs(PlatformType.ANDROID)) {
            return invoke('createNotificationChannel', channelConfig);
        }
        return Promise.resolve();
    };
    /**
     * This method deletes the InstanceId, revoking all tokens.
     *
     * @returns {Promise<void>} Async call to native implementation
     */
    FirebaseMessagingCordovaInterface.prototype.deleteInstanceId = function () {
        return invoke('deleteInstanceId');
    };
    /**
     * Gets ios device's current APNS token
     *
     * @returns {Promise<string>} Returns a Promise that resolves with the APNS token
     */
    FirebaseMessagingCordovaInterface.prototype.getAPNSToken = function () {
        if (this.platformIs(PlatformType.IOS)) {
            return invoke('getAPNSToken');
        }
        return Promise.resolve('');
    };
    /**
     * Retrieves the message that, on tap, opened the app
     *
     * @private
     *
     * @returns {Promise<INotificationPayload | null>} Async call to native implementation
     */
    FirebaseMessagingCordovaInterface.prototype.getInitialPushPayload = function () {
        return invoke('getInitialPushPayload');
    };
    /**
     * Gets device's current registration id
     *
     * @returns {Promise<string>} Returns a Promise that resolves with the registration id token
     */
    FirebaseMessagingCordovaInterface.prototype.getToken = function () {
        return invoke('getToken');
    };
    /**
     * Checking for permissions.
     *
     * @returns {Promise<boolean | null>} Returns a Promise of:
     * - true: push was allowed (or platform is android)
     * - false: push will not be available
     * - null: still not answered, recommended checking again later.
     */
    FirebaseMessagingCordovaInterface.prototype.hasPermission = function () {
        if (this.platformIs(PlatformType.IOS)) {
            return invoke('hasPermission');
        }
        return invoke('hasPermission').then(function (value) { return !!value; });
    };
    /**
     * Request push notification permission, alerting the user if it not have yet decided
     *
     * @param {IRequestPushPermissionOptions} options Options for push request
     * @returns {Promise<boolean>} Returns a Promise that resolves with the permission status
     */
    FirebaseMessagingCordovaInterface.prototype.requestPushPermission = function (options) {
        var _a, _b, _c, _d;
        if (this.platformIs(PlatformType.IOS)) {
            var ios9SupportTimeout = (_b = (_a = options === null || options === void 0 ? void 0 : options.ios9Support) === null || _a === void 0 ? void 0 : _a.timeout) !== null && _b !== void 0 ? _b : 10;
            var ios9SupportInterval = (_d = (_c = options === null || options === void 0 ? void 0 : options.ios9Support) === null || _c === void 0 ? void 0 : _c.interval) !== null && _d !== void 0 ? _d : 0.3;
            return invoke('requestPushPermission', ios9SupportTimeout, ios9SupportInterval);
        }
        return Promise.resolve(true);
    };
    /**
     * Subscribes you to a [topic](https://firebase.google.com/docs/notifications/android/console-topics)
     *
     * @param {string} topic Topic to be subscribed to
     *
     * @returns {Promise<void>} Async call to native implementation
     */
    FirebaseMessagingCordovaInterface.prototype.subscribeToTopic = function (topic) {
        return invoke('subscribeToTopic', topic);
    };
    /**
     * Unsubscribes you from a [topic](https://firebase.google.com/docs/notifications/android/console-topics)
     *
     * @param {string} topic Topic to be unsubscribed from
     *
     * @returns {Promise<void>} Async call to native implementation
     */
    FirebaseMessagingCordovaInterface.prototype.unsubscribeFromTopic = function (topic) {
        return invoke('unsubscribeFromTopic', topic);
    };
    FirebaseMessagingCordovaInterface.prototype.initDifferentAccount = function (accountInfo) {
        return invoke('initDifferentAccount', accountInfo);
    };
    return FirebaseMessagingCordovaInterface;
}());
exports.FirebaseMessagingCordovaInterface = FirebaseMessagingCordovaInterface;
exports.FirebaseMessaging = new FirebaseMessagingCordovaInterface();

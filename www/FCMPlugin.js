'use strict';


var FCMLogger = (function () {
    function FCMLogger() {
        this.buffer = [];
        this.mOnLogCallback = null;
    }
    FCMLogger.prototype.onLog = function (callback) {
        if (typeof callback === 'function') {
            this.mOnLogCallback = callback;
            for (var _i = 0, _a = this.buffer; _i < _a.length; _i++) {
                var ev = _a[_i];
                this.mOnLogCallback(ev);
            }
            this.buffer = [];
        }
    };
    FCMLogger.prototype.log = function (message) {
        var params = [];
        for (var _i = 1; _i < arguments.length; _i++) {
            params[_i - 1] = arguments[_i];
        }
        this.captureLogEvent(0, message, params);
    };
    FCMLogger.prototype.warn = function (message) {
        var params = [];
        for (var _i = 1; _i < arguments.length; _i++) {
            params[_i - 1] = arguments[_i];
        }
        this.captureLogEvent(1, message, params);
    };
    FCMLogger.prototype.error = function (message) {
        var params = [];
        for (var _i = 1; _i < arguments.length; _i++) {
            params[_i - 1] = arguments[_i];
        }
        this.captureLogEvent(2, message, params);
    };
    FCMLogger.prototype.captureLogEvent = function (level, message, params) {
        var ev = { level: level, message: message, params: params, timestamp: Date.now() };
        if (this.mOnLogCallback) {
            this.mOnLogCallback(ev);
        }
        else {
            this.bufferEvent(ev);
        }
    };
    FCMLogger.prototype.bufferEvent = function (ev) {
        this.buffer.push(ev);
        while (this.buffer.length > 1000) {
            this.buffer.shift();
        }
    };
    return FCMLogger;
}());
var logger = new FCMLogger();

var execAsPromise = function (command, args) {
    if (args === void 0) { args = []; }
    return new Promise(function (resolve, reject) {
        window.cordova.exec(resolve, reject, 'FCMPlugin', command, args);
    });
};

var asDisposableListener = function (eventTarget, eventName, callback, options) {
    if (options === void 0) { options = {}; }
    var once = options.once;
    var handler = function (event) { return callback(event.detail); };
    eventTarget.addEventListener(eventName, handler, { passive: true, once: once });
    return {
        dispose: function () { return eventTarget.removeEventListener(eventName, handler); },
    };
};

var bridgeNativeEvents = function (eventTarget) {
    var onError = function (error) {
        logger.error('Error listening to native events', error);
    };
    var onEvent = function (data) {
        try {
            var _a = JSON.parse(data), eventName = _a[0], eventData = _a[1];
            eventTarget.dispatchEvent(new CustomEvent(eventName, { detail: eventData }));
        }
        catch (error) {
            logger.error('Error parsing native event data', error);
        }
    };
    window.cordova.exec(onEvent, onError, 'FCMPlugin', 'startJsEventBridge', []);
};

var FCMPlugin = (function () {
    function FCMPlugin() {
        this.eventTarget = document.createElement('div');
        logger.log('plugin webview wrapper has been created');
    }
    FCMPlugin.prototype.onLog = function (callback) {
        logger.onLog(callback);
    };

    FCMPlugin.prototype.init = function () {
        return execAsPromise('ready')
            .catch((error) => { return logger.error('Ready error: ', error); })
            .then(() => {
                logger.log('FCM Ready!');
                bridgeNativeEvents(this.eventTarget);
            });
    };

    FCMPlugin.prototype.clearAllNotifications = function () {
        return execAsPromise('clearAllNotifications');
    };
    FCMPlugin.prototype.createNotificationChannel = function (channelConfig) {
        if (window.cordova.platformId !== 'android') {
            return Promise.resolve();
        }
        return execAsPromise('createNotificationChannel', [channelConfig]);
    };
    FCMPlugin.prototype.deleteInstanceId = function () {
        return execAsPromise('deleteInstanceId');
    };
    FCMPlugin.prototype.getAPNSToken = function () {
        return window.cordova.platformId !== 'ios'
            ? Promise.resolve('')
            : execAsPromise('getAPNSToken');
    };
    FCMPlugin.prototype.getInitialPushPayload = function () {
        return execAsPromise('getInitialPushPayload');
    };
    FCMPlugin.prototype.getToken = function () {
        return execAsPromise('getToken');
    };
    FCMPlugin.prototype.hasPermission = function () {
        return window.cordova.platformId === 'ios'
            ? execAsPromise('hasPermission')
            : execAsPromise('hasPermission').then(function (value) { return !!value; });
    };
    FCMPlugin.prototype.onNotification = function (callback, options) {
        return asDisposableListener(this.eventTarget, 'notification', callback, options);
    };
    FCMPlugin.prototype.onTokenRefresh = function (callback, options) {
        return asDisposableListener(this.eventTarget, 'tokenRefresh', callback, options);
    };
    FCMPlugin.prototype.requestPushPermission = function (options) {
        var _a, _b, _c, _d;
        if (window.cordova.platformId !== 'ios') {
            return Promise.resolve(true);
        }
        var ios9SupportTimeout = (_b = (_a = options === null || options === void 0 ? void 0 : options.ios9Support) === null || _a === void 0 ? void 0 : _a.timeout) !== null && _b !== void 0 ? _b : 10;
        var ios9SupportInterval = (_d = (_c = options === null || options === void 0 ? void 0 : options.ios9Support) === null || _c === void 0 ? void 0 : _c.interval) !== null && _d !== void 0 ? _d : 0.3;
        return execAsPromise('requestPushPermission', [ios9SupportTimeout, ios9SupportInterval]);
    };
    FCMPlugin.prototype.subscribeToTopic = function (topic) {
        return execAsPromise('subscribeToTopic', [topic]);
    };
    FCMPlugin.prototype.unsubscribeFromTopic = function (topic) {
        return execAsPromise('unsubscribeFromTopic', [topic]);
    };
    FCMPlugin.prototype.initDifferentAccount = function (accountInfo) {
        return execAsPromise('initDifferentAccount', [accountInfo]);
    };
    return FCMPlugin;
}());

var FCM = new FCMPlugin();

module.exports = FCM;

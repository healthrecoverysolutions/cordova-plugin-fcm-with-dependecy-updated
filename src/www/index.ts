export type { IChannelConfiguration } from './IChannelConfiguration'
export type { IRequestPushPermissionOptions } from './IRequestPushPermissionOptions'
export type { INotificationPayload } from './INotificationPayload'
export type { IDisposable } from './IDisposable'
export { FCMLogger, FCMLogEventLevel } from './FCMLogger'
export type { FCMLogEvent, FCMLogEventCallback } from './FCMLogger'
import { FCMPlugin } from './FCMPlugin'

interface Window {
    FCM: FCMPlugin
}

export const FCM = new FCMPlugin()
export { FCMPlugin }
export default FCM

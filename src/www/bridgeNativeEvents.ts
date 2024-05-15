import { logger } from './FCMLogger'

declare var window: {
    cordova: {
        exec: Function
    }
}

/**
 * This is a simple helper to Promisify the calls to cordova
 *
 * @param {eventTarget} EventTarget EventTarget for native-sourced custom events.
 *
 * @returns {void}
 */
export const bridgeNativeEvents = (eventTarget: EventTarget): void => {
    const onError = (error: Error) => {
        logger.error('Error listening to native events', error)
    }
    const onEvent = (data: string) => {
        try {
            const [eventName, eventData] = JSON.parse(data)
            eventTarget.dispatchEvent(new CustomEvent(eventName, { detail: eventData }))
        } catch (error) {
            logger.error('Error parsing native event data', error)
        }
    }
    window.cordova.exec(onEvent, onError, 'FCMPlugin', 'startJsEventBridge', [])
}

export const enum FCMLogEventLevel {
    LOG = 0,
    WARN = 1,
    ERROR = 2
}

export interface FCMLogEvent {
    timestamp: number;
    message: string;
    level: FCMLogEventLevel;
    params: any[];
}

export type FCMLogEventCallback = (ev: FCMLogEvent) => void;

export class FCMLogger {

    private buffer: FCMLogEvent[] = [];
    private mOnLogCallback: FCMLogEventCallback | null = null;

    public onLog(callback: FCMLogEventCallback): void {
        if (typeof callback === 'function') {
            this.mOnLogCallback = callback;
            // notify new callback of any buffered events
            for (const ev of this.buffer) {
                this.mOnLogCallback(ev);
            }
            this.buffer = [];
        }
    }

    public log(message: string, ...params: any[]): void {
        this.captureLogEvent(FCMLogEventLevel.LOG, message, params);
    }

    public warn(message: string, ...params: any[]): void {
        this.captureLogEvent(FCMLogEventLevel.WARN, message, params);
    }

    public error(message: string, ...params: any[]): void {
        this.captureLogEvent(FCMLogEventLevel.ERROR, message, params);
    }

    private captureLogEvent(level: FCMLogEventLevel, message: string, params: any[]): void {
        const ev: FCMLogEvent = {level, message, params, timestamp: Date.now()};
        if (this.mOnLogCallback) {
            this.mOnLogCallback(ev);
        } else {
            this.bufferEvent(ev);
        }
    }

    private bufferEvent(ev: FCMLogEvent): void {
        this.buffer.push(ev);
        // make sure this doesn't grow infinitely
        while (this.buffer.length > 1000) {
            this.buffer.shift();
        }
    }
}

export const logger = new FCMLogger();
export declare const enum FCMLogEventLevel {
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
export declare type FCMLogEventCallback = (ev: FCMLogEvent) => void;
export declare class FCMLogger {
    private buffer;
    private mOnLogCallback;
    onLog(callback: FCMLogEventCallback): void;
    log(message: string, ...params: any[]): void;
    warn(message: string, ...params: any[]): void;
    error(message: string, ...params: any[]): void;
    private captureLogEvent;
    private bufferEvent;
}
export declare const logger: FCMLogger;

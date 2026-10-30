export const start: (privateDirectory: string) => Promise<string>;
export const command: (command: string, target: string, timeoutMs: number) => Promise<string>;
export const cancel: () => void;

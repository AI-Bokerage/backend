export interface SendSmsInput {
  to: string;
  body: string;
}

export interface ISmsSender {
  send(input: SendSmsInput): Promise<void>;
}

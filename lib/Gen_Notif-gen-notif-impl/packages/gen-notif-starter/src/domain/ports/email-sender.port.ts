export interface SendEmailInput {
  to: string;
  subject: string;
  html?: string;
  text: string;
}

export interface IEmailSender {
  send(input: SendEmailInput): Promise<void>;
}

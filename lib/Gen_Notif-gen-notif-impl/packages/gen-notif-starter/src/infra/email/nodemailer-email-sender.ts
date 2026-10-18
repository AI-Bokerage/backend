import nodemailer, { type Transporter } from "nodemailer";
import type { IEmailSender, SendEmailInput } from "../../domain/ports/email-sender.port.ts";

export interface NodemailerConfig {
  host: string;
  port: number;
  secure: boolean;
  user?: string;
  pass?: string;
  from: string;
}

export class NodemailerEmailSender implements IEmailSender {
  private readonly transporter: Transporter;
  private readonly from: string;

  constructor(config: NodemailerConfig) {
    this.from = config.from;
    this.transporter = nodemailer.createTransport({
      host: config.host,
      port: config.port,
      secure: config.secure,
      auth: config.user ? { user: config.user, pass: config.pass } : undefined,
    });
  }

  async send(input: SendEmailInput): Promise<void> {
    await this.transporter.sendMail({
      from: this.from,
      to: input.to,
      subject: input.subject,
      text: input.text,
      html: input.html,
    });
  }
}

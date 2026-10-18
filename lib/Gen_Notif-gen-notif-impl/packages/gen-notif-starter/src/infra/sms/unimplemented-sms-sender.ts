import type { ISmsSender, SendSmsInput } from "../../domain/ports/sms-sender.port.ts";
import { SmsNotConfiguredError } from "../../common/errors.ts";

export class UnimplementedSmsSender implements ISmsSender {
  async send(_input: SendSmsInput): Promise<void> {
    throw new SmsNotConfiguredError();
  }
}

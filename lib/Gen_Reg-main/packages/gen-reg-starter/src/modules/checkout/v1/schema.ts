// src/modules/checkout/v1/schema.ts
import { z } from "zod";

export const CreateCheckoutSchema = z.object({
  sessionId: z.string().uuid(),
  successUrl: z.string().url(),
  cancelUrl: z.string().url(),
  paymentProvider: z.enum(["stripe", "razorpay"]).optional(),
});

export type CreateCheckoutInput = z.infer<typeof CreateCheckoutSchema>;

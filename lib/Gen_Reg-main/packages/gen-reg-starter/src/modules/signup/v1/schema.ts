// src/modules/signup/v1/schema.ts
import { z } from "zod";
import { SLUG_REGEX, SLUG_MIN_LEN, SLUG_MAX_LEN } from "../../../common/slug.ts";

export const StartSignupSchema = z.object({
  email: z.string().email("Invalid email address").max(255),
  password: z.string().min(8, "Password must be at least 8 characters").max(128),
  fullName: z.string().min(1).max(255).optional(),
  companyName: z.string().min(1).max(255).optional(),
  phone: z.string().min(7).max(32).optional(),
  source: z.string().min(1).max(64).default("web"),
  referralCode: z.string().max(64).optional(),
  utmSource: z.string().max(128).optional(),
  utmMedium: z.string().max(128).optional(),
  utmCampaign: z.string().max(128).optional(),
  desiredSubdomain: z
    .string()
    .min(SLUG_MIN_LEN)
    .max(SLUG_MAX_LEN)
    .regex(SLUG_REGEX, "Subdomain must start with a letter, end with a letter or digit, and contain only lowercase letters, digits, and hyphens"),
});

export type StartSignupInput = z.infer<typeof StartSignupSchema>;

export const SubdomainCheckSchema = z.object({
  value: z.string().min(1).max(100),
});

export type SubdomainCheckInput = z.infer<typeof SubdomainCheckSchema>;

export const ResumeSignupSchema = z.object({
  token: z.string().min(1).max(128),
});

export type ResumeSignupInput = z.infer<typeof ResumeSignupSchema>;

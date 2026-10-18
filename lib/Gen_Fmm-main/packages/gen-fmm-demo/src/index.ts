import { createGenFmm } from "@gen-ms/gen-fmm-starter";
import { getPrismaClient } from "@gen-ms/gen-fmm-starter";

const PORT = Number(process.env.PORT ?? "3700");

const genFmm = createGenFmm({
  // onFlagChanged is a no-op by default; a multi-pod host would wire this to
  // its own pub/sub (Redis, SNS, whatever it already runs) to broadcast
  // cache invalidation to every other pod's in-process L1.
  onFlagChanged: (flagKey, tenantId) => {
    console.log(`[demo] flag changed: ${flagKey}${tenantId ? ` (tenant ${tenantId})` : " (global)"}`);
  },
});

async function seed(): Promise<void> {
  const prisma = getPrismaClient();
  await prisma.module.upsert({
    where: { code: "reporting" },
    create: { code: "reporting", name: "Reporting", category: "analytics" },
    update: {},
  });
  await prisma.featureFlag.upsert({
    where: { key: "new_dashboard" },
    create: { key: "new_dashboard", moduleCode: "reporting", defaultEnabled: false },
    update: {},
  });
  await prisma.featureFlag.upsert({
    where: { key: "beta_export" },
    create: { key: "beta_export", moduleCode: null, isGradualRollout: true, rolloutPercentage: 50 },
    update: {},
  });
}

// Host owns telemetry-flush scheduling — no internal timer, matching the
// rest of the Gen_MS family's "host supplies the cron" convention.
setInterval(() => {
  void genFmm.flushTelemetryBuffer().then((count) => {
    if (count > 0) console.log(`[demo] flushed ${count} telemetry events`);
  });
}, 30_000);

seed()
  .then(() => {
    genFmm.app.listen(PORT, () => {
      console.log(`[demo] Gen_FMM demo listening on http://localhost:${PORT}`);
      console.log(`[demo] docs at http://localhost:${PORT}/docs`);
    });
  })
  .catch((err) => {
    console.error("[demo] seed failed", err);
    process.exit(1);
  });

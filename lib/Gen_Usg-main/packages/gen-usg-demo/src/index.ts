import { createGenUsg, registerMeter, type ILimitProvider } from "@gen-ms/gen-usg-starter";

registerMeter("seats", { unit: "seat" });
registerMeter("api_calls", { unit: "call", graceEligible: true });
registerMeter("storage_bytes", { unit: "byte", mode: "resource" });

// A stub ILimitProvider for the demo — a real host would resolve this from
// their own billing/plan lookup. Fixed limits here just make the smoke
// script's ALLOW->GRACE->BLOCK walkthrough reproducible.
const demoLimitProvider: ILimitProvider = {
  async getLimits() {
    return { seats: 5, api_calls: 3, storage_bytes: -1 };
  },
};

const genUsg = createGenUsg({ limitProvider: demoLimitProvider });

const PORT = Number(process.env.PORT ?? 3600);
genUsg.app.listen(PORT, () => {
  console.log(`gen-usg-demo listening on :${PORT}`);
});

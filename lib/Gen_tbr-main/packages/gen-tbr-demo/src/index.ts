import { createGenTbr } from "@gen-ms/gen-tbr-starter";

const PORT = process.env.PORT ? Number(process.env.PORT) : 3400;

const { app } = createGenTbr({});

app.listen(PORT, () => {
  console.log(`gen-tbr-demo listening on http://localhost:${PORT}`);
});

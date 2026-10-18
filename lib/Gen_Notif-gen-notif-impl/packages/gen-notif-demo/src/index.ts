import http from "node:http";
import { createGenNotif, registerTemplate } from "@gen-ms/gen-notif-starter";

registerTemplate("doc.uploaded", (data) => ({
  title: "New document uploaded",
  body: `${data["fileName"]} was uploaded by ${data["uploadedBy"]}`,
  html: `<p><strong>${data["fileName"]}</strong> was uploaded by ${data["uploadedBy"]}.</p>`,
}));

const genNotif = createGenNotif({});
const server = http.createServer(genNotif.app);
genNotif.attachRealtime?.(server);

const PORT = Number(process.env.PORT ?? 3500);
server.listen(PORT, () => {
  console.log(`gen-notif-demo listening on :${PORT} (Socket.IO path /gen-notif/ws)`);
});

-- Flow 4.9: approve/dismiss of an AI-suggested task is recorded on its notes-svc run.
ALTER TABLE "Task" ADD COLUMN "aiRunId" TEXT;

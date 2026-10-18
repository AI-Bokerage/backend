-- Flow 4.9: suggested tasks come from notes-svc; one task per AI suggestion.
ALTER TABLE "Task" ADD COLUMN "aiSuggestionId" TEXT;
CREATE UNIQUE INDEX "Task_aiSuggestionId_key" ON "Task"("aiSuggestionId");

-- CreateEnum
CREATE TYPE "AppointmentType" AS ENUM ('CALL', 'CONSULTATION', 'DOCUMENT_REVIEW');

-- AlterTable
ALTER TABLE "Appointment" ADD COLUMN     "type" "AppointmentType" NOT NULL DEFAULT 'CONSULTATION';


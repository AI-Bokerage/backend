export interface AnnouncementDto {
  id: string;
  title: string;
  body: string;
  type: string;
  targetSegment: string;
  channels: string[];
  scheduledFor: string | null;
  sentAt: string | null;
  createdBy: string;
  createdAt: string;
}

export interface CreateAnnouncementInput {
  title: string;
  body: string;
  type: string;
  targetSegment: string;
  channels: string[];
  scheduledFor?: string;
  createdBy: string;
}

export interface ListAnnouncementsQuery {
  type?: string;
  page: number;
  pageSize: number;
}

export interface ListAnnouncementsResult {
  announcements: AnnouncementDto[];
  page: number;
  pageSize: number;
  total: number;
}

export interface DispatchScheduledResult {
  dispatched: number;
  failed: number;
}

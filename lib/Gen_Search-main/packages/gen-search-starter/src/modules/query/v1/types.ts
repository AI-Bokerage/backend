export interface SearchResultDto {
  entityType: string;
  entityId: string;
  title: string;
  snippet?: string;
  score: number;
}

export interface SearchResponseDto {
  data: SearchResultDto[];
}

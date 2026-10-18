import type { SearchResultItem } from "../../../domain/ports/search-query.port.ts";
import type { SearchResultDto } from "./types.ts";

export function mapResultToDto(result: SearchResultItem): SearchResultDto {
  return {
    entityType: result.entityType,
    entityId: result.entityId,
    title: result.title,
    snippet: result.snippet,
    score: result.score,
  };
}

/** Matches backend/src/main/kotlin/com/rally26/common/web/PageResponse.kt exactly (ADR-102). */
export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
}

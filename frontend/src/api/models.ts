/** 성공 JSON 본문이며 Query 캐시에서도 envelope를 유지한다. */
export type ApiResponse<T> = { data: T; traceId: string };

/** 페이지 번호는 0부터 시작하며 빈 결과의 items는 빈 배열이다. */
export type PageData<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

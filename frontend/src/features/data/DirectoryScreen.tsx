'use client';

import type { ColDef } from 'ag-grid-community';
import Link from 'next/link';
import { useState } from 'react';

import { useSearchDepartments, useSearchUsers } from '@/api/generated/client';
import { DataGrid } from '@/components/ui/DataGrid';
import { FormField } from '@/components/ui/Form';
import { SearchInput } from '@/components/ui/Inputs';
import { SearchPanel } from '@/components/ui/Search';
import { PermissionGuard, usePermission, useSession } from '@/features/auth/AuthProvider';
import { DEFAULT_DIRECTORY_VIEW, useUiStore } from '@/stores/UiStore';

import { departmentSchema, pageSchema, readEnvelope, userSummarySchema } from './contracts';

type DirectoryRow = { id: string; name: string; status: string; description: string };
const sortOptions = [
  { value: 'idAsc', label: 'ID 오름차순' },
  { value: 'nameAsc', label: '이름 오름차순' },
  { value: 'nameDesc', label: '이름 내림차순' },
];

/** 서버가 허용한 정렬·페이지를 실제 생성 Hook에 전달하고 목록 복귀 조건은 탭 메모리에 유지한다. */
export function DirectoryScreen({ kind }: { kind: 'users' | 'departments' }) {
  return (
    <PermissionGuard permission={kind === 'users' ? 'USER_READ' : 'DEPARTMENT_READ'}>
      <DirectoryContent kind={kind} />
    </PermissionGuard>
  );
}

/** 사용자·부서의 응답 형태만 정규화하며 서버 한 페이지에 client 정렬을 적용하지 않는다. */
function DirectoryContent({ kind }: { kind: 'users' | 'departments' }) {
  const { generation, status } = useSession();
  const canReadPersonal = usePermission('PERSONAL_INFO_READ');
  const view = useUiStore((state) => state.directories[kind]);
  const setDirectory = useUiStore((state) => state.setDirectory);
  const [open, setOpen] = useState(true);
  const params = { q: view.q || undefined, page: view.page, size: view.size, sort: view.sort };
  const users = useSearchUsers(params, {
    query: {
      queryKey: ['directory', generation, 'users', params],
      enabled: kind === 'users' && status === 'authenticated',
      refetchOnWindowFocus: false,
      select: (response) => {
        const envelope = readEnvelope(pageSchema(userSummarySchema), response);
        return {
          ...envelope,
          data: {
            ...envelope.data,
            items: envelope.data.items.map((item) => ({
              id: item.userId,
              name: item.displayName,
              status: item.status,
              description: item.departments
                .map((department) => department.departmentName)
                .join(', '),
            })),
          },
        };
      },
    },
  });
  const departments = useSearchDepartments(params, {
    query: {
      queryKey: ['directory', generation, 'departments', params],
      enabled: kind === 'departments' && status === 'authenticated',
      refetchOnWindowFocus: false,
      select: (response) => {
        const envelope = readEnvelope(pageSchema(departmentSchema), response);
        return {
          ...envelope,
          data: {
            ...envelope.data,
            items: envelope.data.items.map((item) => ({
              id: item.departmentId,
              name: item.departmentName,
              status: item.active ? '활성' : '비활성',
              description: item.departmentCode,
            })),
          },
        };
      },
    },
  });
  const query = kind === 'users' ? users : departments;
  const columns: ColDef<DirectoryRow>[] = [
    { field: 'id', headerName: 'ID' },
    { field: 'name', headerName: kind === 'users' ? '이름' : '부서명' },
    { field: 'status', headerName: '상태' },
    { field: 'description', headerName: kind === 'users' ? '소속 부서' : '부서 코드' },
    ...(kind === 'users' && canReadPersonal
      ? [
          {
            headerName: '상세',
            cellRenderer: ({ data }: { data?: DirectoryRow }) =>
              data ? <Link href={`/users/${encodeURIComponent(data.id)}`}>프로필 보기</Link> : null,
          },
        ]
      : []),
  ];

  return (
    <>
      <h1>{kind === 'users' ? '사용자' : '부서'}</h1>
      <SearchPanel
        open={open}
        onOpenChange={setOpen}
        busy={query.isFetching}
        onSearch={() => {
          setDirectory(kind, { ...view, q: view.draft, page: 0 });
          if (view.q === view.draft && view.page === 0) void query.refetch();
        }}
        onReset={() => setDirectory(kind, { ...DEFAULT_DIRECTORY_VIEW })}
      >
        <FormField label="검색어" help="조회 버튼을 누르면 검색어를 적용합니다.">
          <SearchInput
            value={view.draft}
            maxLength={100}
            onChange={(event) => setDirectory(kind, { ...view, draft: event.target.value })}
          />
        </FormField>
      </SearchPanel>
      <DataGrid
        mode="server"
        rows={query.data?.data.items ?? []}
        columns={columns}
        rowKey={(row) => row.id}
        loading={query.isPending || query.isFetching}
        error={query.error?.message}
        errorTraceId={query.error?.traceId}
        onRetry={() => void query.refetch()}
        pagination={{
          page: view.page,
          size: view.size,
          totalElements: query.data?.data.totalElements ?? 0,
          onChange: (page, size) => setDirectory(kind, { ...view, page, size }),
        }}
        sort={{
          value: view.sort,
          options: sortOptions,
          onChange: (sort) => {
            if (sort === 'idAsc' || sort === 'nameAsc' || sort === 'nameDesc') {
              setDirectory(kind, { ...view, sort, page: 0 });
            }
          },
        }}
      />
    </>
  );
}

'use client';

import type { ColDef } from 'ag-grid-community';
import { useState } from 'react';

import { useSearchAccessAudits, useSearchSecurityAudits } from '@/api/generated/client';
import { DataGrid, dateColumn } from '@/components/ui/DataGrid';
import { InlineAlert } from '@/components/ui/Feedback';
import { FormField } from '@/components/ui/Form';
import { DateRangeInput, TextInput } from '@/components/ui/Inputs';
import { SearchPanel } from '@/components/ui/Search';
import { PermissionGuard, useSession } from '@/features/auth/AuthProvider';
import { formatApiDate, validateDateRange } from '@/lib/dates';

import { accessSchema, pageSchema, readEnvelope, securitySchema } from './contracts';

type AuditFilter = { from: string; to: string; id: string; code: string };
type AuditRow = {
  id: string;
  occurredAt: string;
  actor: string;
  code: string;
  outcome: string;
  target: string;
  traceId: string;
};

/** 권한별 조회 자체도 감사 대상이므로 유효 기간의 명시 조회와 페이지 이동만 요청한다. */
export function AuditScreen({ kind }: { kind: 'access' | 'security' }) {
  return (
    <PermissionGuard permission={kind === 'access' ? 'AUDIT_READ' : 'SECURITY_AUDIT_READ'}>
      <AuditContent kind={kind} />
    </PermissionGuard>
  );
}

/** 기본 최근 하루를 편집하고 적용한 UTC 조건만 생성 Hook·QueryKey에 전달한다. */
function AuditContent({ kind }: { kind: 'access' | 'security' }) {
  const { generation } = useSession();
  const [draft, setDraft] = useState<AuditFilter>(() => ({
    from: formatApiDate(new Date(Date.now() - 86400000), 'millisecond'),
    to: formatApiDate(new Date(), 'millisecond'),
    id: '',
    code: '',
  }));
  const [applied, setApplied] = useState<AuditFilter>();
  const [paging, setPaging] = useState({ page: 0, size: 20 });
  const [open, setOpen] = useState(true);
  const [validation, setValidation] = useState('');
  const [dateError, setDateError] = useState<string>();
  const common = { from: applied?.from ?? draft.from, to: applied?.to ?? draft.to, ...paging };
  const accessParams = {
    ...common,
    actorId: applied?.id || undefined,
    resourceCode: applied?.code || undefined,
  };
  const securityParams = {
    ...common,
    targetUserId: applied?.id || undefined,
    eventCode: applied?.code || undefined,
  };
  const access = useSearchAccessAudits(accessParams, {
    query: {
      queryKey: ['audit', generation, 'access', accessParams],
      enabled: kind === 'access' && Boolean(applied),
      refetchOnWindowFocus: false,
      refetchOnReconnect: false,
      select: (response) => {
        const envelope = readEnvelope(pageSchema(accessSchema), response);
        return {
          ...envelope,
          data: {
            ...envelope.data,
            items: envelope.data.items.map((item) => ({
              id: item.auditId,
              occurredAt: item.occurredAt,
              actor: item.actorUserId ?? '익명',
              code: `${item.resourceCode} / ${item.actionCode}`,
              outcome: item.outcome,
              target: item.targetId ?? '-',
              traceId: item.traceId,
            })),
          },
        };
      },
    },
  });
  const security = useSearchSecurityAudits(securityParams, {
    query: {
      queryKey: ['audit', generation, 'security', securityParams],
      enabled: kind === 'security' && Boolean(applied),
      refetchOnWindowFocus: false,
      refetchOnReconnect: false,
      select: (response) => {
        const envelope = readEnvelope(pageSchema(securitySchema), response);
        return {
          ...envelope,
          data: {
            ...envelope.data,
            items: envelope.data.items.map((item) => ({
              id: item.eventId,
              occurredAt: item.occurredAt,
              actor: item.actorUserId ?? '익명',
              code: item.eventCode,
              outcome: item.outcome,
              target: item.targetUserId ?? '-',
              traceId: item.traceId,
            })),
          },
        };
      },
    },
  });
  const query = kind === 'access' ? access : security;
  const columns: ColDef<AuditRow>[] = [
    dateColumn<AuditRow>({ field: 'occurredAt', headerName: '발생 · Asia/Seoul', minWidth: 240 }),
    { field: 'actor', headerName: '행위자 ID' },
    { field: 'code', headerName: '코드', minWidth: 220 },
    { field: 'outcome', headerName: '결과' },
    { field: 'target', headerName: '대상 ID' },
    { field: 'traceId', headerName: '진단 ID', minWidth: 280 },
  ];

  /** 최대 31일·종료 미포함·ID 문자열·코드 allowlist를 검사하며 잘못된 조건을 전송하지 않는다. */
  function search() {
    if (
      dateError ||
      !validateDateRange(draft.from, draft.to, 'millisecond', 31) ||
      draft.from === draft.to ||
      (draft.id &&
        (!/^[1-9][0-9]{0,18}$/.test(draft.id) || BigInt(draft.id) > 9223372036854775807n)) ||
      (draft.code && !/^[A-Z_]{1,64}$/.test(draft.code))
    ) {
      setValidation(
        '기간은 시작 < 종료, 최대 31일입니다. ID는 양의 정수 문자열, 코드는 영문 대문자·밑줄 64자 이내입니다.',
      );
      return;
    }

    setValidation('');
    setPaging({ page: 0, size: paging.size });
    setApplied({ ...draft });
    if (applied && JSON.stringify(applied) === JSON.stringify(draft) && paging.page === 0)
      void query.refetch();
  }

  return (
    <>
      <h1>{kind === 'access' ? '접근 이력' : '보안 이력'}</h1>
      <p>
        화면에는 서울 시각을 표시하고 서버에는 UTC로 전달합니다. 최대 31일까지 조회할 수 있으며 종료
        시각은 포함하지 않습니다. IP 주소와 브라우저 원문 정보는 표시하지 않습니다.
      </p>
      <SearchPanel
        open={open}
        onOpenChange={setOpen}
        busy={query.isFetching}
        onSearch={search}
        onReset={() => {
          setDraft({ ...draft, id: '', code: '' });
          setApplied(undefined);
          setPaging({ page: 0, size: 20 });
          setValidation('');
        }}
      >
        <DateRangeInput
          onValidation={setDateError}
          from={draft.from}
          to={draft.to}
          precision="millisecond"
          maxDays={31}
          onChange={(from, to) => setDraft({ ...draft, from, to })}
        />
        <FormField label={kind === 'access' ? '행위자 ID' : '대상 사용자 ID'}>
          <TextInput
            value={draft.id}
            maxLength={19}
            onChange={(event) => setDraft({ ...draft, id: event.target.value })}
          />
        </FormField>
        <FormField label={kind === 'access' ? '리소스 코드' : '사건 코드'}>
          <TextInput
            value={draft.code}
            maxLength={64}
            onChange={(event) => setDraft({ ...draft, code: event.target.value })}
          />
        </FormField>
      </SearchPanel>
      {validation && <InlineAlert tone="error">{validation}</InlineAlert>}
      {!applied ? (
        <InlineAlert>기간과 조건을 정한 뒤 조회하세요.</InlineAlert>
      ) : (
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
            ...paging,
            totalElements: query.data?.data.totalElements ?? 0,
            onChange: (page, size) => setPaging({ page, size }),
          }}
        />
      )}
    </>
  );
}

'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useRouter } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { Button } from '@/components/ui/Button';
import { FormActions, FormError, FormErrorSummary, FormField } from '@/components/ui/Form';
import { PasswordInput, TextInput } from '@/components/ui/Inputs';
import { Badge, InlineAlert, useToast } from '@/components/ui/Feedback';
import styles from '@/components/ui/ui.module.css';
import { ProfilePanel } from '@/features/data/ProfilePanel';
import { sessionStatusLabels } from '@/messages/messages';

import { PermissionGuard, useSession } from './AuthProvider';
import { applyApiFormError } from './formErrors';

const loginSchema = z.object({
  loginId: z
    .string()
    .regex(/^[a-z0-9][a-z0-9._-]{2,49}$/, '아이디는 소문자 영문·숫자·._-의 3~50자입니다.'),
  password: z
    .string()
    .refine(
      (value) => [...value].length > 0 && [...value].length <= 128,
      '비밀번호는 필수이며 128자 이내입니다.',
    ),
});
const passwordSchema = z
  .object({
    currentPassword: z
      .string()
      .refine(
        (value) => [...value].length > 0 && [...value].length <= 128,
        '현재 비밀번호는 필수이며 128자 이내입니다.',
      ),
    newPassword: z
      .string()
      .refine(
        (value) => [...value].length >= 15 && [...value].length <= 128,
        '새 비밀번호는 15~128자입니다.',
      ),
    confirmPassword: z.string(),
  })
  .refine((value) => value.newPassword === value.confirmPassword, {
    path: ['confirmPassword'],
    message: '확인 비밀번호가 일치하지 않습니다.',
  });

/** 실제 인증은 사용자 제출로만 시작하며 자격 정보를 저장하거나 로그에 출력하지 않는다. */
export function LoginScreen() {
  const session = useSession();
  const router = useRouter();
  const form = useForm<z.infer<typeof loginSchema>>({
    resolver: zodResolver(loginSchema),
    defaultValues: { loginId: '', password: '' },
    reValidateMode: 'onChange',
  });

  /** 실패 시 입력을 유지하고 성공 시 메모리 입력을 지운 뒤 내 정보로 이동한다. */
  async function submit(values: z.infer<typeof loginSchema>) {
    try {
      await session.login(values.loginId, values.password);
      form.reset();
      router.replace('/me');
    } catch (error) {
      applyApiFormError(error, form, ['loginId', 'password']);
    }
  }

  return (
    <section className={styles.panel}>
      <h1>로그인</h1>
      <p className={styles.help}>
        연결된 서버의 계정으로 로그인하세요. 로그인 정보는 안전한 쿠키로 관리합니다.
      </p>
      <form className={styles.formLayout} onSubmit={form.handleSubmit(submit)} noValidate>
        <FormErrorSummary
          items={(
            [
              ['loginId', '아이디'],
              ['password', '비밀번호'],
            ] as const
          ).flatMap(([key, label]) => {
            const message = form.formState.errors[key]?.message;

            return message ? [{ key, label, message, onFocus: () => form.setFocus(key) }] : [];
          })}
        />
        <FormField label="아이디" required error={form.formState.errors.loginId?.message}>
          <TextInput
            {...form.register('loginId')}
            autoComplete="username"
            disabled={form.formState.isSubmitting}
          />
        </FormField>
        <FormField label="비밀번호" required error={form.formState.errors.password?.message}>
          <PasswordInput
            {...form.register('password')}
            autoComplete="current-password"
            disabled={form.formState.isSubmitting}
          />
        </FormField>
        <FormError>{form.formState.errors.root?.message}</FormError>
        <FormActions submitting={form.formState.isSubmitting} submitLabel="로그인" />
      </form>
      <p>화면 예제와 달리 실제 서버에 로그인합니다. 테스트 계정만 사용하세요.</p>
      <Button
        onClick={() => {
          void session.bootstrap(true);
        }}
      >
        기존 로그인 확인
      </Button>
      <p role="status">로그인 상태: {sessionStatusLabels[session.status]}</p>
      {session.error && <InlineAlert tone="error">{session.error.message}</InlineAlert>}
      {session.status === 'authenticated' && (
        <Button onClick={() => router.replace('/me')}>내 정보로 이동</Button>
      )}
    </section>
  );
}

/** 현재 사용자와 비밀번호 폼을 인증 경계 안에서만 마운트해 전환 시 입력도 폐기한다. */
export function MeScreen() {
  return (
    <PermissionGuard>
      <MeContent />
    </PermissionGuard>
  );
}

/** 현재 사용자는 Query에서 읽고 서버 비밀번호 변경 성공 후 재로그인을 안내한다. */
function MeContent() {
  const session = useSession();
  const notify = useToast();
  const form = useForm<z.infer<typeof passwordSchema>>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
    reValidateMode: 'onChange',
  });

  /** 확인 값은 프론트 검증 전용이며 API 본문에는 현재·새 비밀번호만 보낸다. */
  async function submit(values: z.infer<typeof passwordSchema>) {
    try {
      await session.changePassword(values.currentPassword, values.newPassword);
      form.reset();
      notify('비밀번호가 변경되었습니다. 다시 로그인해 주세요.', 'success');
    } catch (error) {
      applyApiFormError(error, form, ['currentPassword', 'newPassword']);
    }
  }

  return (
    <div className={styles.pageStack}>
      <h1>내 정보</h1>
      <section className={styles.panel}>
        <h2>기본 정보</h2>
        <ProfilePanel />
      </section>
      <section className={styles.panel}>
        <h2>사용 권한</h2>
        <p className={styles.help}>서버가 확인한 현재 계정의 권한입니다.</p>
        <ul className={styles.permissions}>
          {session.user?.permissions.map((permission) => (
            <li key={permission}>
              <Badge>{permission}</Badge>
            </li>
          ))}
        </ul>
      </section>
      <section className={styles.panel}>
        <h2>로그인 관리</h2>
        <div className={styles.row}>
          <Button
            onClick={() => {
              void session
                .logout()
                .catch(() => notify('서버의 로그아웃 완료를 확인하지 못했습니다.', 'error'));
            }}
          >
            현재 로그인 종료
          </Button>
          <Button
            variant="danger"
            onClick={() => {
              void session
                .logout(true)
                .catch(() => notify('전체 로그인 종료를 확인하지 못했습니다.', 'error'));
            }}
          >
            전체 로그인 종료
          </Button>
        </div>
      </section>
      <section className={`${styles.panel} ${styles.formPanel}`}>
        <h2>비밀번호 변경</h2>
        <InlineAlert>
          비밀번호를 사용하는 계정에서 변경할 수 있습니다. 변경하면 현재 계정을 사용하는 모든
          로그인이 종료됩니다.
        </InlineAlert>
        <form className={styles.formLayout} onSubmit={form.handleSubmit(submit)} noValidate>
          <FormErrorSummary
            items={(
              [
                ['currentPassword', '현재 비밀번호'],
                ['newPassword', '새 비밀번호'],
                ['confirmPassword', '새 비밀번호 확인'],
              ] as const
            ).flatMap(([key, label]) => {
              const message = form.formState.errors[key]?.message;

              return message ? [{ key, label, message, onFocus: () => form.setFocus(key) }] : [];
            })}
          />
          <FormField
            label="현재 비밀번호"
            required
            error={form.formState.errors.currentPassword?.message}
          >
            <PasswordInput
              {...form.register('currentPassword')}
              autoComplete="current-password"
              disabled={form.formState.isSubmitting}
            />
          </FormField>
          <FormField
            label="새 비밀번호"
            required
            error={form.formState.errors.newPassword?.message}
          >
            <PasswordInput
              {...form.register('newPassword')}
              autoComplete="new-password"
              disabled={form.formState.isSubmitting}
            />
          </FormField>
          <FormField
            label="새 비밀번호 확인"
            required
            error={form.formState.errors.confirmPassword?.message}
          >
            <PasswordInput
              {...form.register('confirmPassword')}
              autoComplete="new-password"
              disabled={form.formState.isSubmitting}
            />
          </FormField>
          <FormError>{form.formState.errors.root?.message}</FormError>
          <FormActions submitting={form.formState.isSubmitting} submitLabel="비밀번호 변경" />
        </form>
      </section>
    </div>
  );
}

'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';

import { FormActions, FormError, FormErrorSummary, FormField } from '@/components/ui/Form';
import { useAction } from '@/providers/ActionProvider';
import { InlineAlert } from '@/components/ui/Feedback';
import { TextInput, Textarea } from '@/components/ui/Inputs';
import { Select, Checkbox } from '@/components/ui/Selections';
import styles from '@/components/ui/ui.module.css';
import { ConfirmDialog } from '@/components/ui/Overlays';

import { ExampleSection } from './ExampleSection';

const schema = z.object({
  name: z.string().min(1, '이름은 필수입니다.').max(100, '100자 이내로 입력하세요.'),
  category: z.string().min(1, '분류를 선택하세요.'),
  memo: z.string().max(300, '300자 이내로 입력하세요.'),
});
type Values = z.infer<typeof schema>;
const defaults: Values = { name: '', category: '', memo: '' };

/** 실제 저장을 가장하지 않는 합성 폼으로 register/Controller·오류·dirty/reset을 실행한다. */
export function FormsExample() {
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: defaults,
    mode: 'onSubmit',
    reValidateMode: 'onChange',
    shouldFocusError: true,
  });
  const [submitted, setSubmitted] = useState<Values>();
  const [confirmReset, setConfirmReset] = useState(false);
  const [fail, setFail] = useState(false);
  const submitAction = useAction('example-form:submit');
  const isSubmitting = form.formState.isSubmitting || submitAction.pending;
  const memo = useWatch({ control: form.control, name: 'memo' });

  useEffect(() => {
    /** 편집 중 새로고침/이탈은 브라우저 기본 확인으로 입력 유실을 경고한다. */
    function handleBeforeUnload(event: BeforeUnloadEvent) {
      if (form.formState.isDirty) event.preventDefault();
    }
    window.addEventListener('beforeunload', handleBeforeUnload);

    /** 같은 탭의 Link 이동도 편집값이 있으면 먼저 유실 여부를 확인한다. */
    function handleLink(event: MouseEvent) {
      const target = event.target instanceof Element ? event.target.closest('a[href]') : null;

      if (
        form.formState.isDirty &&
        target instanceof HTMLAnchorElement &&
        target.target !== '_blank' &&
        !event.ctrlKey &&
        !event.metaKey &&
        !window.confirm('저장하지 않은 입력을 버리고 이동할까요?')
      ) {
        event.preventDefault();
        event.stopPropagation();
      }
    }

    document.addEventListener('click', handleLink, true);

    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
      document.removeEventListener('click', handleLink, true);
    };
  }, [form.formState.isDirty]);

  /** 합성 실패 선택 시 입력을 보존하며 서버 저장 성공이라고 표시하지 않는다. */
  async function submit(values: Values) {
    try {
      const result = await submitAction.run(async () => {
        await new Promise((resolve) => setTimeout(resolve, 500));

        if (fail) throw new Error('제출 실패 예제');

        return values;
      });

      if (result.status === 'completed') {
        setSubmitted(result.value);
        form.reset(result.value);
      }
    } catch {
      form.setError('root', {
        message:
          '제출 실패를 재현했습니다. 입력한 내용은 남아 있습니다. 실제 서버에서 발생한 오류는 아닙니다.',
      });
    }
  }

  return (
    <ExampleSection
      title="입력 검증과 제출 테스트"
      result={
        form.formState.errors.root ? (
          <p>제출 실패를 재현했습니다. 입력 영역의 오류와 남아 있는 값을 확인하세요.</p>
        ) : submitted ? (
          <InlineAlert tone="success">
            입력을 확인했습니다: {submitted.name} / {submitted.category}. 이 예제는 서버에 저장하지
            않습니다.
          </InlineAlert>
        ) : (
          <p>
            제출 테스트에 성공하면 확인한 입력이 여기에 표시됩니다. 이 예제는 서버에 저장하지
            않습니다.
          </p>
        )
      }
      description="빈 상태로 제출하면 첫 오류로 이동합니다. 값을 수정하면 오류가 해제됩니다. 실패 모드를 켜면 입력이 보존되고, 초기화는 확인 후 실행됩니다. 실제 저장 API는 호출하지 않습니다."
      code={
        'const form = useForm({ resolver: zodResolver(schema) });\n<TextInput {...form.register(\'name\')} />\n<Controller name="category" control={form.control} render={...} />'
      }
    >
      <form className={styles.formLayout} onSubmit={form.handleSubmit(submit)} noValidate>
        <FormErrorSummary
          items={(
            [
              ['name', '이름'],
              ['category', '분류'],
              ['memo', '설명'],
            ] as const
          ).flatMap(([key, label]) => {
            const message = form.formState.errors[key]?.message;

            return message ? [{ key, label, message, onFocus: () => form.setFocus(key) }] : [];
          })}
        />
        <FormField label="이름" required error={form.formState.errors.name?.message}>
          <TextInput {...form.register('name')} maxLength={100} disabled={isSubmitting} />
        </FormField>
        <Controller
          name="category"
          control={form.control}
          render={({ field }) => (
            <FormField label="분류" required error={form.formState.errors.category?.message}>
              <Select
                ref={field.ref}
                onBlur={field.onBlur}
                label="분류"
                options={[
                  { value: 'design', label: '디자인' },
                  { value: 'engineering', label: '엔지니어링' },
                ]}
                value={field.value}
                onChange={field.onChange}
                disabled={isSubmitting}
                placeholder="분류를 선택하세요"
              />
            </FormField>
          )}
        />
        <FormField label="설명" error={form.formState.errors.memo?.message}>
          <Textarea
            {...form.register('memo')}
            value={memo}
            rows={3}
            maxLength={300}
            showCount
            autoResize
            disabled={isSubmitting}
          />
        </FormField>
        <Checkbox label="제출 실패 재현" checked={fail} onChange={setFail} />
        <FormError>{form.formState.errors.root?.message}</FormError>
        <p>입력 변경: {form.formState.isDirty ? '있음' : '없음'}</p>
        <FormActions
          submitting={isSubmitting}
          onReset={() => (form.formState.isDirty ? setConfirmReset(true) : form.reset(defaults))}
          submitLabel="제출 테스트"
        />
      </form>
      <ConfirmDialog
        open={confirmReset}
        onOpenChange={setConfirmReset}
        title="입력 초기화"
        description="입력한 값을 지우고 기본값으로 돌아갑니다."
        confirmLabel="초기화"
        intent="danger"
        onConfirm={() => {
          form.reset(defaults);
          setSubmitted(undefined);
          setConfirmReset(false);
        }}
      />
    </ExampleSection>
  );
}

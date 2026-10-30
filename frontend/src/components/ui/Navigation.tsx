'use client';

import {
  Tabs as PrimitiveTabs,
  Tooltip as PrimitiveTooltip,
  Collapsible as PrimitiveCollapsible,
  DropdownMenu,
} from 'radix-ui';
import type { ReactElement, ReactNode } from 'react';

import { Button } from './Button';
import { ClientOnly } from './ClientOnly';
import { Icon } from './Icon';
import styles from './ui.module.css';

/** 제어된 active 값과 Radix 키보드 이동을 제공한다. */
export function Tabs({
  value,
  onChange,
  items,
  label,
}: {
  value: string;
  onChange: (value: string) => void;
  label: string;
  items: { value: string; label: string; content: ReactNode; disabled?: boolean }[];
}) {
  return (
    <ClientOnly fallback={<span aria-busy="true">{label} 준비 중</span>}>
      <PrimitiveTabs.Root value={value} onValueChange={onChange}>
        <PrimitiveTabs.List className={styles.tabs} aria-label={label}>
          {items.map((item) => (
            <PrimitiveTabs.Trigger
              key={item.value}
              value={item.value}
              className={styles.tab}
              disabled={item.disabled}
            >
              {item.label}
            </PrimitiveTabs.Trigger>
          ))}
        </PrimitiveTabs.List>
        {items.map((item) => (
          <PrimitiveTabs.Content key={item.value} value={item.value}>
            {item.content}
          </PrimitiveTabs.Content>
        ))}
      </PrimitiveTabs.Root>
    </ClientOnly>
  );
}

/** hover와 키보드 focus 모두에서 보조 설명을 표시한다. */
export function Tooltip({ text, children }: { text: string; children: ReactElement }) {
  return (
    <PrimitiveTooltip.Provider delayDuration={250}>
      <PrimitiveTooltip.Root>
        <PrimitiveTooltip.Trigger asChild>{children}</PrimitiveTooltip.Trigger>
        <PrimitiveTooltip.Portal>
          <PrimitiveTooltip.Content className={styles.floating} sideOffset={5}>
            {text}
          </PrimitiveTooltip.Content>
        </PrimitiveTooltip.Portal>
      </PrimitiveTooltip.Root>
    </PrimitiveTooltip.Provider>
  );
}

/** 열림 상태를 부모가 관리하는 접힘 영역이다. */
export function Collapsible({
  label,
  open,
  onChange,
  children,
}: {
  label: string;
  open: boolean;
  onChange: (open: boolean) => void;
  children: ReactNode;
}) {
  return (
    <PrimitiveCollapsible.Root open={open} onOpenChange={onChange}>
      <PrimitiveCollapsible.Trigger asChild>
        <Button>
          {label} {open ? '접기' : '펼치기'}
        </Button>
      </PrimitiveCollapsible.Trigger>
      <PrimitiveCollapsible.Content>{children}</PrimitiveCollapsible.Content>
    </PrimitiveCollapsible.Root>
  );
}

/** modal=false로 scroll-lock 스타일을 피하고 선택 가능한 조작을 메뉴로 제공한다. */
export function ActionMenu({
  label,
  actions,
}: {
  label: string;
  actions: { id: string; label: string; disabled?: boolean; onSelect: () => void }[];
}) {
  return (
    <DropdownMenu.Root modal={false}>
      <DropdownMenu.Trigger asChild>
        <Button>
          {label}
          <Icon name="down" />
        </Button>
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content className={styles.floating} sideOffset={4}>
          {actions.map((action) => (
            <DropdownMenu.Item
              key={action.id}
              className={styles.option}
              disabled={action.disabled}
              onSelect={action.onSelect}
            >
              {action.label}
            </DropdownMenu.Item>
          ))}
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}

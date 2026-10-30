'use client';

import {
  BarChart as EBarChart,
  LineChart as ELineChart,
  PieChart as EPieChart,
} from 'echarts/charts';
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import { init, use as registerCharts, type EChartsCoreOption } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { useEffect, useRef, useState } from 'react';

import { toChartNumber } from '@/lib/decimal';

import { EmptyState, ErrorState, Spinner } from './Feedback';
import styles from './ui.module.css';

registerCharts([
  EBarChart,
  ELineChart,
  EPieChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
]);
export type ChartRow = { label: string; value: string };
type ChartProps = {
  title: string;
  rows: ChartRow[];
  kind: 'bar' | 'line' | 'pie';
  loading?: boolean;
  error?: string;
  onRetry?: () => void;
};

/** 기존 canvas에만 init하여 DOM cssText 생성 경로와 HTML tooltip을 피하고 dispose한다. */
function ChartCanvas({ title, rows, kind }: ChartProps) {
  const host = useRef<HTMLDivElement>(null);
  const canvas = useRef<HTMLCanvasElement>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    const element = canvas.current;
    const parent = host.current;

    if (!element || !parent) {
      return;
    }

    let chart: ReturnType<typeof init> | undefined;
    const observer = new ResizeObserver(() => {
      if (parent.clientWidth === 0 || parent.clientHeight === 0) {
        return;
      }

      try {
        if (!chart) {
          chart = init(element, undefined, {
            renderer: 'canvas',
            width: parent.clientWidth,
            height: parent.clientHeight,
          });
          const data = rows.map((row) => ({ name: row.label, value: toChartNumber(row.value) }));
          const tokens = getComputedStyle(element);
          const option: EChartsCoreOption = {
            animation: false,
            color: [
              '--color-primary',
              '--color-success',
              '--color-warning',
              '--color-chart-fourth',
            ].map((token) => tokens.getPropertyValue(token).trim()),
            tooltip: { trigger: kind === 'pie' ? 'item' : 'axis', renderMode: 'richText' },
            legend: { show: kind === 'pie' },
            ...(kind === 'pie'
              ? {}
              : {
                  grid: { containLabel: true },
                  xAxis: { type: 'category', data: rows.map((row) => row.label) },
                  yAxis: { type: 'value' },
                }),
            series: [
              {
                name: title,
                type: kind,
                data: kind === 'pie' ? data : data.map((row) => row.value),
              },
            ],
          };
          chart.setOption(option);
        }

        chart.resize({ width: parent.clientWidth, height: parent.clientHeight });
        setError(false);
      } catch {
        chart?.dispose();
        chart = undefined;
        setError(true);
      }
    });
    observer.observe(parent);

    return () => {
      observer.disconnect();
      chart?.dispose();
    };
  }, [title, rows, kind]);

  return (
    <>
      {error && <ErrorState message="차트 표시 범위 또는 렌더링 상태를 확인하세요." />}
      <div ref={host} className={styles.chart}>
        <canvas ref={canvas} role="img" aria-label={`${title}, 정확한 값은 아래 표 참조`} />
      </div>
    </>
  );
}

/** 차트 좌표는 근삿값으로 표시하며 정확한 원문 문자열을 접근 가능한 표로 제공한다. */
export function Chart(props: ChartProps) {
  if (props.loading) {
    return <Spinner />;
  }

  if (props.error) {
    return <ErrorState message={props.error} onRetry={props.onRetry} />;
  }

  if (!props.rows.length) {
    return <EmptyState message="차트에 표시할 데이터가 없습니다." />;
  }

  return (
    <section>
      <h3>{props.title}</h3>
      <ChartCanvas {...props} />
      <p className={styles.help}>
        차트 좌표는 절댓값 1조 이내의 number 근삿값입니다. 정확한 값은 표의 문자열입니다.
      </p>
      <table>
        <caption>{props.title} 원본 값</caption>
        <thead>
          <tr>
            <th>항목</th>
            <th>정확한 값</th>
          </tr>
        </thead>
        <tbody>
          {props.rows.map((row) => (
            <tr key={row.label}>
              <th scope="row">{row.label}</th>
              <td>{row.value}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

/** 막대 비교를 공통 canvas 생명주기에 연결한다. */
export function BarChart(props: Omit<ChartProps, 'kind'>) {
  return <Chart {...props} kind="bar" />;
}
/** 시간 순서 변화도 같은 원본 표와 오류 경계를 공유한다. */
export function LineChart(props: Omit<ChartProps, 'kind'>) {
  return <Chart {...props} kind="line" />;
}
/** 비중 표시는 HTML formatter 없이 canvas tooltip을 사용한다. */
export function PieChart(props: Omit<ChartProps, 'kind'>) {
  return <Chart {...props} kind="pie" />;
}

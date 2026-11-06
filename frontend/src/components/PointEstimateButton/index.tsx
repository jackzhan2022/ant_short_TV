import { ReloadOutlined } from '@ant-design/icons';
import { Button, type ButtonProps, Tooltip } from 'antd';
import { useId } from 'react';
import {
  formatPoints,
  type PointEstimateOperation,
} from '@/services/point-estimate';
import usePointEstimate from './usePointEstimate';

export type PointEstimateButtonProps = ButtonProps & {
  operations: PointEstimateOperation[] | undefined;
  tenantId?: number;
  estimateEnabled?: boolean;
  showEstimate?: boolean;
  displayMode?: 'label' | 'points';
};

export default function PointEstimateButton({
  operations,
  tenantId,
  estimateEnabled = true,
  showEstimate = true,
  displayMode = 'label',
  children,
  disabled,
  loading,
  onClick,
  ...props
}: PointEstimateButtonProps) {
  const quote = usePointEstimate(operations, {
    tenantId,
    enabled: estimateEnabled && !disabled && !loading,
  });
  return (
    <PointEstimateButtonView
      {...props}
      disabled={disabled}
      loading={loading}
      onClick={onClick}
      quote={quote}
      showEstimate={showEstimate}
      displayMode={displayMode}
      free={operations?.length === 0}
    >
      {children}
    </PointEstimateButtonView>
  );
}

export function PointEstimateButtonView({
  quote,
  children,
  disabled,
  loading,
  showEstimate = true,
  displayMode = 'label',
  free = false,
  onClick,
  ...props
}: ButtonProps & {
  quote: ReturnType<typeof usePointEstimate>;
  showEstimate?: boolean;
  displayMode?: 'label' | 'points';
  free?: boolean;
}) {
  const descriptionId = useId();
  const pointsOnly = displayMode === 'points';
  const amount = quote.estimate
    ? formatPoints(Math.ceil(quote.estimate.estimatedPoints))
    : free
      ? '0'
      : '—';
  const suffix = quote.loading
    ? '预估中'
    : quote.error
      ? '预估失败'
      : quote.estimate
        ? `${pointsOnly ? amount : `预计 ${formatPoints(quote.estimate.estimatedPoints)}`} 积分${quote.estimate.sufficient ? '' : ' · 积分不足'}`
        : undefined;
  return (
    <Tooltip
      title={
        quote.reason ||
        (pointsOnly
          ? `${typeof children === 'string' ? children : '开始创作'} · ${quote.loading ? '积分计算中' : quote.error ? '积分获取失败' : `${amount} 积分`}`
          : !showEstimate
            ? suffix
            : undefined)
      }
    >
      <span
        style={{
          display: props.block ? 'block' : 'inline-flex',
          maxWidth: '100%',
        }}
      >
        <Button
          {...props}
          style={{
            ...props.style,
            flexWrap: pointsOnly ? 'nowrap' : 'wrap',
            whiteSpace: pointsOnly ? 'nowrap' : 'normal',
            ...(pointsOnly ? {} : { height: 'auto' }),
          }}
          disabled={disabled || quote.disabled || Boolean(loading)}
          loading={loading || quote.loading}
          aria-label={
            props['aria-label'] ||
            (typeof children === 'string' ? children : undefined)
          }
          aria-describedby={
            [props['aria-describedby'], suffix ? descriptionId : undefined]
              .filter(Boolean)
              .join(' ') || undefined
          }
          onClick={(event) => {
            if (!quote.disabled && !disabled && !loading) onClick?.(event);
          }}
        >
          {pointsOnly ? amount : children}
          {suffix && !pointsOnly ? (
            <span
              id={descriptionId}
              aria-live="polite"
              hidden={pointsOnly || !showEstimate}
              style={{ fontSize: '0.9em' }}
            >
              {' '}
              · {suffix}
            </span>
          ) : null}
        </Button>
        {pointsOnly && suffix ? (
          <span id={descriptionId} hidden aria-live="polite">
            {suffix}
          </span>
        ) : null}
        {(quote.error || quote.estimate?.sufficient === false) &&
        !disabled &&
        !loading ? (
          <Button
            type="link"
            size="small"
            aria-label={pointsOnly ? '刷新积分' : '重新预估积分'}
            icon={pointsOnly ? <ReloadOutlined /> : undefined}
            onClick={quote.refresh}
          >
            {pointsOnly ? null : '重新预估'}
          </Button>
        ) : null}
      </span>
    </Tooltip>
  );
}

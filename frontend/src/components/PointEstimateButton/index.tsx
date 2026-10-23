import { Button, type ButtonProps, Tooltip } from 'antd';
import { useId } from 'react';
import {
  type PointEstimateOperation,
  formatPoints,
} from '@/services/point-estimate';
import usePointEstimate from './usePointEstimate';

export type PointEstimateButtonProps = ButtonProps & {
  operations: PointEstimateOperation[] | undefined;
  tenantId?: number;
  estimateEnabled?: boolean;
};

export default function PointEstimateButton({
  operations,
  tenantId,
  estimateEnabled = true,
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
  onClick,
  ...props
}: ButtonProps & {
  quote: ReturnType<typeof usePointEstimate>;
}) {
  const descriptionId = useId();
  const suffix = quote.loading
    ? '预估中'
    : quote.error
      ? '预估失败'
      : quote.estimate
        ? `预计 ${formatPoints(quote.estimate.estimatedPoints)} 积分${quote.estimate.sufficient ? '' : ' · 积分不足'}`
        : undefined;
  return (
    <Tooltip title={quote.reason}>
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
            flexWrap: 'wrap',
            whiteSpace: 'normal',
            height: 'auto',
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
          {children}
          {suffix ? (
            <span
              id={descriptionId}
              aria-live="polite"
              style={{ fontSize: '0.9em' }}
            >
              {' '}
              · {suffix}
            </span>
          ) : null}
        </Button>
        {(quote.error || quote.estimate?.sufficient === false) &&
        !disabled &&
        !loading ? (
          <Button
            type="link"
            size="small"
            aria-label="重新预估积分"
            onClick={quote.refresh}
          >
            重新预估
          </Button>
        ) : null}
      </span>
    </Tooltip>
  );
}

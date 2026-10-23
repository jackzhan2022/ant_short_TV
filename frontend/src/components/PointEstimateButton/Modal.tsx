import { Modal, type ModalProps } from 'antd';
import { useRef } from 'react';
import type { PointEstimateOperation } from '@/services/point-estimate';
import usePointEstimate from './usePointEstimate';
import { PointEstimateButtonView } from '.';

export default function PointEstimateModal({
  operations,
  tenantId,
  ...props
}: ModalProps & {
  operations: PointEstimateOperation[] | undefined;
  tenantId?: number;
}) {
  const open = useRef(props.open);
  open.current = props.open;
  const quote = usePointEstimate(operations, {
    tenantId,
    enabled:
      Boolean(props.open) &&
      !props.confirmLoading &&
      !props.okButtonProps?.disabled &&
      !props.okButtonProps?.loading,
  });
  return (
    <Modal
      {...props}
      footer={(_, { CancelBtn }) => (
        <>
          <CancelBtn />
          <PointEstimateButtonView
            type="primary"
            {...props.okButtonProps}
            quote={quote}
            loading={props.confirmLoading || props.okButtonProps?.loading}
            onClick={(event) => {
              if (open.current)
                props.onOk?.(
                  event as Parameters<NonNullable<ModalProps['onOk']>>[0],
                );
            }}
          >
            {props.okText || '确定'}
          </PointEstimateButtonView>
        </>
      )}
    />
  );
}

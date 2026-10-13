import { DownOutlined } from '@ant-design/icons';
import { Button, Popover } from 'antd';
import { useState } from 'react';
import { useStyles } from './index.style';
import {
  findTeamIcon,
  isTeamLogoImage,
  TEAM_ICON_OPTIONS,
  TeamIcon,
  teamIconLabel,
} from './teamIcons';

type TeamIconPickerProps = {
  id?: string;
  value?: string | null;
  onChange?: (value: string | undefined) => void;
  disabled?: boolean;
};

const TeamIconPicker = ({
  id,
  value,
  onChange,
  disabled,
}: TeamIconPickerProps) => {
  const { styles, cx } = useStyles();
  const [open, setOpen] = useState(false);
  const selectedKey =
    findTeamIcon(value)?.key || (isTeamLogoImage(value) ? undefined : 'team');
  const preview = (
    <span className={styles.preview}>
      <TeamIcon value={value} size={24} />
      <span>{teamIconLabel(value)}</span>
    </span>
  );

  if (disabled) return <div className={styles.readonly}>{preview}</div>;

  return (
    <div className={styles.control}>
      <Popover
        trigger="click"
        placement="bottomLeft"
        open={open}
        onOpenChange={setOpen}
        content={
          <div className={styles.panel}>
            <p className={styles.heading}>选择一个团队图标</p>
            <fieldset className={styles.grid} aria-label="可选团队图标">
              {TEAM_ICON_OPTIONS.map((option) => (
                <Button
                  key={option.key}
                  aria-label={option.label}
                  aria-pressed={option.key === selectedKey}
                  className={cx(
                    styles.option,
                    option.key === selectedKey && styles.selected,
                  )}
                  onClick={() => {
                    onChange?.(`icon:${option.key}`);
                    setOpen(false);
                  }}
                >
                  <span aria-hidden="true">
                    <option.Icon />
                  </span>
                  <span className={styles.optionLabel}>{option.label}</span>
                </Button>
              ))}
            </fieldset>
            <p className={styles.hint}>一个人创作，也可以拥有自己的团队标识</p>
          </div>
        }
      >
        <Button
          id={id}
          aria-label="选择团队图标"
          aria-expanded={open}
          className={styles.trigger}
        >
          {preview}
          <DownOutlined aria-hidden="true" />
        </Button>
      </Popover>
      {value && (
        <Button
          type="text"
          aria-label="恢复默认图标"
          onClick={() => {
            onChange?.(undefined);
            setOpen(false);
          }}
        >
          恢复默认
        </Button>
      )}
    </div>
  );
};

export default TeamIconPicker;

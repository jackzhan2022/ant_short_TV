import { request } from '@umijs/max';
import { Select } from 'antd';
import { useEffect, useId, useState } from 'react';

export const defaultAssetExtractionSkill = 'short-drama-asset-recognition-framework';

type SkillOption = {
  code: string;
  name: string;
  description: string;
};

export default function AssetExtractionSkillSelect({
  value = defaultAssetExtractionSkill,
  onChange,
  disabled,
}: {
  value?: string;
  onChange: (code: string) => void;
  disabled?: boolean;
}) {
  const id = useId();
  const [options, setOptions] = useState<SkillOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);
    request<{ data: SkillOption[] }>('/api/projects/asset-extraction-skills')
      .then((response) => { if (active) setOptions(response.data); })
      .catch(() => { if (active) setError(true); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [attempt]);
  const selected = options.find((option) => option.code === value);
  return (
    <div style={{ width: '100%' }}>
      <label htmlFor={id}>资产提取方案</label>
      <Select
        id={id}
        aria-label="资产提取方案"
        aria-describedby={`${id}-help`}
        style={{ width: '100%', marginTop: 8 }}
        value={value}
        loading={loading}
        disabled={disabled || loading || error}
        onChange={(code) => onChange(code)}
        options={[
          ...(!selected ? [{ value, label: value === defaultAssetExtractionSkill ? '默认资产提取' : '当前方案（暂不可用）', disabled: true }] : []),
          ...options.map((option) => ({ value: option.code, label: option.code === defaultAssetExtractionSkill ? '默认资产提取' : option.name })),
        ]}
      />
      <p id={`${id}-help`} style={{ marginTop: 8, marginBottom: 16 }}>
        {error ? <>方案加载失败。<button type="button" onClick={() => setAttempt((current) => current + 1)}>重新加载</button></>
          : selected?.description || '决定角色、场景和道具的设定、构图及提示词写法。'}
      </p>
    </div>
  );
}

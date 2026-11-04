import { Button, Flex, Image, Radio, Select, Spin, Typography } from 'antd';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { queryStyleLibrary } from '@/pages/style-library/service';
import styles from './index.module.css';
import { type ArtStyleOption, queryArtStyles } from './service';

export type ArtStyleSelection = {
  visualStyleVersion?: 'V1' | 'V2';
  visualStyleCode?: string;
  visualStyle?: string;
};

export default function ProjectArtStylePicker({
  value,
  onChange,
  legacyPicker,
}: {
  value: ArtStyleSelection;
  onChange: (value: ArtStyleSelection) => void;
  legacyPicker?: ReactNode;
}) {
  const version = value.visualStyleVersion || 'V1';
  const remembered = useRef<Partial<Record<'V1' | 'V2', ArtStyleSelection>>>(
    {},
  );
  const [catalog, setCatalog] = useState<ArtStyleOption[]>([]);
  const [legacy, setLegacy] = useState<{ label: string; value: string }[]>([]);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const [retry, setRetry] = useState(0);
  const customLegacy = legacyPicker !== undefined;

  useEffect(() => {
    remembered.current[version] = {
      visualStyleVersion: version,
      visualStyleCode: value.visualStyleCode,
      visualStyle: value.visualStyle,
    };
  }, [version, value]);

  useEffect(() => {
    if (version === 'V1' && customLegacy) return;
    let active = true;
    setLoading(true);
    setFailed(false);
    const load =
      version === 'V2'
        ? queryArtStyles().then((response) => {
            if (active) setCatalog(response.data);
          })
        : queryStyleLibrary({ keyword, pageSize: 100 }).then((response) => {
            if (active)
              setLegacy(
                response.data.data.map((style) => ({
                  label: style.name,
                  value: style.name,
                })),
              );
          });
    load
      .catch(() => {
        if (active) setFailed(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [version, customLegacy, keyword, retry]);

  const selected = catalog.find(
    (style) => style.code === value.visualStyleCode,
  );
  const legacyOptions =
    value.visualStyle &&
    !legacy.some((style) => style.value === value.visualStyle)
      ? [{ label: value.visualStyle, value: value.visualStyle }, ...legacy]
      : legacy;

  return (
    <Flex vertical gap={12}>
      <Radio.Group
        aria-label="画风版本"
        optionType="button"
        buttonStyle="solid"
        options={[
          { label: '画风 1.0', value: 'V1' },
          { label: '画风 2.0', value: 'V2' },
        ]}
        value={version}
        onChange={(event) => {
          const next = event.target.value as 'V1' | 'V2';
          remembered.current[version] = {
            visualStyleVersion: version,
            visualStyleCode: value.visualStyleCode,
            visualStyle: value.visualStyle,
          };
          setKeyword('');
          onChange(
            remembered.current[next] || {
              visualStyleVersion: next,
              visualStyleCode: '',
              visualStyle: '',
            },
          );
        }}
      />
      <Typography.Text type="secondary">
        {version === 'V2'
          ? '使用 11 种画风及对应资产美术规则，优化角色、场景、道具提示词。'
          : '沿用现有画风库和资产提示词规则。'}
        分镜技能独立配置，切换不会自动覆盖已有提示词和图片。
        {version === 'V2' && '点击示例图可放大，点击名称选择画风。'}
      </Typography.Text>
      {version === 'V1' && customLegacy ? (
        legacyPicker
      ) : (
        <>
          {version === 'V2' ? (
            <section
              className={styles.gallery}
              aria-label="画风 2.0 示例图"
              aria-busy={loading}
            >
              {loading && catalog.length === 0 && (
                <Spin aria-label="正在加载画风" />
              )}
              {catalog.map((style) => {
                const active = value.visualStyleCode === style.code;
                return (
                  <div
                    className={active ? styles.selectedCard : styles.card}
                    key={style.code}
                  >
                    <Image
                      src={style.imageUrl}
                      alt={`${style.name}示例图`}
                      width="100%"
                      height={156}
                      loading="lazy"
                      style={{ objectFit: 'contain' }}
                    />
                    <div className={styles.cardBody}>
                      <Typography.Text type="secondary">
                        {style.category}
                      </Typography.Text>
                      <Button
                        block
                        type={active ? 'primary' : 'default'}
                        aria-label={`选择${style.name}`}
                        aria-pressed={active}
                        title={style.description}
                        onClick={() =>
                          onChange({
                            visualStyleVersion: 'V2',
                            visualStyleCode: style.code,
                            visualStyle: style.name,
                          })
                        }
                      >
                        {style.name}
                      </Button>
                    </div>
                  </div>
                );
              })}
            </section>
          ) : (
            <Select
              aria-label="画风 1.0 风格"
              placeholder="请选择画风"
              loading={loading}
              status={failed ? 'error' : undefined}
              value={value.visualStyle || undefined}
              showSearch={{ filterOption: false, onSearch: setKeyword }}
              options={legacyOptions}
              onChange={(choice: string) =>
                onChange({
                  visualStyleVersion: 'V1',
                  visualStyleCode: '',
                  visualStyle: choice,
                })
              }
            />
          )}
          {failed && (
            <div role="alert">
              画风加载失败{' '}
              <Button
                size="small"
                onClick={() => setRetry((current) => current + 1)}
              >
                重试
              </Button>
            </div>
          )}
          {version === 'V2' && selected && (
            <Typography.Text type="secondary">
              {selected.description}
            </Typography.Text>
          )}
        </>
      )}
    </Flex>
  );
}

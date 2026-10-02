import { PageContainer } from '@ant-design/pro-components';
import {
  Card,
  Empty,
  Flex,
  Image,
  Input,
  Pagination,
  Segmented,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import LazyMediaImage from '@/components/LazyMediaImage';
import {
  type PublicStyle,
  queryStyleCategories,
  queryStyleLibrary,
} from './service';

const StyleLibraryPage = () => {
  const [styles, setStyles] = useState<PublicStyle[]>([]);
  const [category, setCategory] = useState('全部');
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [categories, setCategories] = useState<string[]>([]);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(24);
  const [total, setTotal] = useState(0);
  const [loadError, setLoadError] = useState('');

  useEffect(() => {
    let alive = true;
    void queryStyleCategories()
      .then((response) => {
        if (alive) setCategories(response.data || []);
      })
      .catch(() => {
        if (alive) setCategories([]);
      });
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setLoadError('');
    queryStyleLibrary({ category, keyword, current, pageSize })
      .then((response) => {
        if (alive) {
          setStyles(response.data.data ?? []);
          setTotal(response.data.total);
        }
      })
      .catch(() => {
        if (alive) {
          setStyles([]);
          setTotal(0);
          setLoadError('风格加载失败，请重试');
        }
      })
      .finally(() => {
        if (alive) {
          setLoading(false);
        }
      });
    return () => {
      alive = false;
    };
  }, [category, keyword, current, pageSize]);

  const grid = useMemo(
    () => ({
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
      gap: 16,
    }),
    [],
  );

  return (
    <PageContainer>
      <Flex vertical gap="large" style={{ width: '100%' }}>
        <Flex vertical gap="medium" style={{ width: '100%' }}>
          <Typography.Title level={3} style={{ margin: 0 }}>
            风格库
          </Typography.Title>
          <Space wrap size="middle">
            <Segmented
              options={['全部', ...categories].map((value) => ({
                label: value,
                value,
              }))}
              value={category}
              onChange={(value) => {
                setCategory(String(value));
                setCurrent(1);
              }}
            />
            <Input.Search
              allowClear
              placeholder="搜索风格名称或描述"
              style={{ width: 280 }}
              onSearch={(value) => {
                setKeyword(value.trim());
                setCurrent(1);
              }}
            />
          </Space>
        </Flex>
        <Spin spinning={loading}>
          {styles.length ? (
            <Image.PreviewGroup>
              <div style={grid}>
                {styles.map((style) => (
                  <Card
                    key={style.externalId}
                    hoverable
                    cover={
                      <LazyMediaImage
                        alt={style.name}
                        src={style.imageUrl}
                        height={140}
                        width="100%"
                        styles={{
                          image: {
                            objectFit: 'cover',
                          },
                        }}
                      />
                    }
                    styles={{
                      body: {
                        minHeight: 164,
                      },
                    }}
                  >
                    <Flex vertical gap="small" style={{ width: '100%' }}>
                      <Typography.Text strong>{style.name}</Typography.Text>
                      <Tag color="blue">{style.category}</Tag>
                      <Typography.Paragraph
                        type="secondary"
                        ellipsis={{ rows: 3 }}
                        style={{ margin: 0 }}
                      >
                        {style.description}
                      </Typography.Paragraph>
                    </Flex>
                  </Card>
                ))}
              </div>
            </Image.PreviewGroup>
          ) : (
            <Empty description={loadError || '没有匹配的公共风格'} />
          )}
        </Spin>
        <Pagination
          current={current}
          pageSize={pageSize}
          total={total}
          showSizeChanger
          pageSizeOptions={[24, 48, 96]}
          onChange={(page, size) => {
            setCurrent(size === pageSize ? page : 1);
            setPageSize(size);
          }}
        />
      </Flex>
    </PageContainer>
  );
};

export default StyleLibraryPage;

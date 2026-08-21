import { PageContainer } from '@ant-design/pro-components';
import {
  Card,
  Empty,
  Flex,
  Image,
  Input,
  Segmented,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { queryStyleLibrary, type PublicStyle } from './service';

const categoryOptions = [
  '全部',
  '3D风格',
  '2D风格',
  '真人风格',
  '漫剧风格',
  '未分组',
  '传统艺术',
  '漫画风格',
  '数字艺术',
  '写实风格',
].map((value) => ({ label: value, value }));

const StyleLibraryPage = () => {
  const [styles, setStyles] = useState<PublicStyle[]>([]);
  const [category, setCategory] = useState('全部');
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    queryStyleLibrary({ category, keyword })
      .then((response) => {
        if (alive) {
          setStyles(response.data ?? []);
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
  }, [category, keyword]);

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
              options={categoryOptions}
              value={category}
              onChange={(value) => setCategory(String(value))}
            />
            <Input.Search
              allowClear
              placeholder="搜索风格名称或描述"
              style={{ width: 280 }}
              onSearch={(value) => setKeyword(value.trim())}
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
                      <Image
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
            <Empty description="没有匹配的公共风格" />
          )}
        </Spin>
      </Flex>
    </PageContainer>
  );
};

export default StyleLibraryPage;

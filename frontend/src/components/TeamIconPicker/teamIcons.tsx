import {
  BookOutlined,
  BulbOutlined,
  CameraOutlined,
  CrownOutlined,
  HeartOutlined,
  PictureOutlined,
  RocketOutlined,
  SmileOutlined,
  StarOutlined,
  TeamOutlined,
  UserOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { useState } from 'react';

export const TEAM_ICON_OPTIONS = [
  { key: 'team', label: '团队', Icon: TeamOutlined },
  { key: 'user', label: '个人', Icon: UserOutlined },
  { key: 'video', label: '影视', Icon: VideoCameraOutlined },
  { key: 'camera', label: '相机', Icon: CameraOutlined },
  { key: 'picture', label: '图片', Icon: PictureOutlined },
  { key: 'star', label: '星星', Icon: StarOutlined },
  { key: 'rocket', label: '火箭', Icon: RocketOutlined },
  { key: 'bulb', label: '灵感', Icon: BulbOutlined },
  { key: 'book', label: '书本', Icon: BookOutlined },
  { key: 'crown', label: '皇冠', Icon: CrownOutlined },
  { key: 'smile', label: '笑脸', Icon: SmileOutlined },
  { key: 'heart', label: '爱心', Icon: HeartOutlined },
] as const;

export const findTeamIcon = (value?: string | null) =>
  TEAM_ICON_OPTIONS.find((option) => value === `icon:${option.key}`);

export const isTeamLogoImage = (value?: string | null): value is string =>
  Boolean(
    value &&
      (/^https?:\/\//i.test(value) ||
        (value.startsWith('/') && !value.startsWith('//'))),
  );

export const teamIconLabel = (value?: string | null) =>
  findTeamIcon(value)?.label ||
  (isTeamLogoImage(value) ? '当前图片 Logo' : '默认团队图标');

export const TeamIcon = ({
  value,
  size = 18,
}: {
  value?: string | null;
  size?: number;
}) => {
  const [failedImage, setFailedImage] = useState<string>();
  if (isTeamLogoImage(value) && value !== failedImage) {
    return (
      <img
        src={value}
        alt="团队图片标识"
        style={{
          width: size,
          height: size,
          objectFit: 'cover',
          borderRadius: 4,
        }}
        onError={() => setFailedImage(value)}
      />
    );
  }
  const option = findTeamIcon(value) || TEAM_ICON_OPTIONS[0];
  const Icon = option.Icon;
  return (
    <span
      role="img"
      aria-label={`团队图标：${option.label}`}
      style={{ display: 'inline-flex', fontSize: size }}
    >
      <Icon aria-hidden />
    </span>
  );
};

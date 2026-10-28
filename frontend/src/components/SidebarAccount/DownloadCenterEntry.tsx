import { DownloadOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { App } from 'antd';
import { useEffect } from 'react';

export default function DownloadCenterEntry() {
  const { message } = App.useApp();
  useEffect(() => {
    const created = () => {
      message.success({
        content: (
          <span>
            已加入下载中心，文件将打包为 ZIP。{' '}
            <button
              type="button"
              className="ant-short-download-link"
              onClick={() => history.push('/downloads')}
            >
              查看任务
            </button>
          </span>
        ),
        duration: 5,
      });
    };
    window.addEventListener('download-center-created', created);
    return () => window.removeEventListener('download-center-created', created);
  }, [message]);
  return (
    <button
      type="button"
      className="ant-short-sidebar-points"
      aria-label="下载中心"
      onClick={() => history.push('/downloads')}
    >
      <span className="ant-short-sidebar-points-icon">
        <DownloadOutlined />
      </span>
      <span>下载中心</span>
    </button>
  );
}

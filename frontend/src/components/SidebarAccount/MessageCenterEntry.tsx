import { BellOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { useEffect, useState } from 'react';
import { messageSummary } from '@/pages/messages/service';

export default function MessageCenterEntry() {
  const [unread, setUnread] = useState(0);
  useEffect(() => {
    let disposed = false;
    let generation = 0;
    const refresh = async () => {
      const id = ++generation;
      try {
        const summary = await messageSummary();
        if (!disposed && id === generation) setUnread(summary.unread);
      } catch {
        /* Keep the entry usable when the count cannot be refreshed. */
      }
    };
    void refresh();
    window.addEventListener('focus', refresh);
    window.addEventListener('message-center-change', refresh);
    const timer = window.setInterval(() => {
      if (!document.hidden) void refresh();
    }, 60000);
    return () => {
      disposed = true;
      window.clearInterval(timer);
      window.removeEventListener('focus', refresh);
      window.removeEventListener('message-center-change', refresh);
    };
  }, []);
  return (
    <button
      className="ant-short-sidebar-points ant-short-sidebar-messages"
      type="button"
      aria-label={unread ? `消息中心，${unread} 条未读` : '消息中心'}
      onClick={() => history.push('/messages')}
    >
      <span className="ant-short-sidebar-points-icon">
        <BellOutlined />
      </span>
      <span>消息中心</span>
      {unread > 0 && (
        <span className="ant-short-sidebar-unread">
          {unread > 99 ? '99+' : unread}
        </span>
      )}
    </button>
  );
}

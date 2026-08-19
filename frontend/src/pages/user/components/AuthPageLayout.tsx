import type { ReactNode } from 'react';

const loginVideoSrc =
  'https://zy-dimnx.oss-cn-shenzhen.aliyuncs.com/posters/loginVideo.mp4';

type AuthPageLayoutProps = {
  children: ReactNode;
};

const AuthPageLayout = ({ children }: AuthPageLayoutProps) => (
  <div
    style={{
      minHeight: '100vh',
      display: 'grid',
      gridTemplateColumns: '5fr 5fr',
      overflow: 'hidden',
      background: '#f3f5ff',
    }}
  >
    <div
      style={{
        position: 'relative',
        minHeight: '100vh',
        overflow: 'hidden',
        background: '#d8d8d8',
      }}
    >
      <video
        autoPlay
        data-testid="login-background-video"
        loop
        muted
        playsInline
        preload="auto"
        src={loginVideoSrc}
        poster={loginVideoSrc}
        style={{
          width: '100%',
          height: '100%',
          objectFit: 'cover',
          display: 'block',
        }}
      >
        <source src={loginVideoSrc} type="video/mp4" />
      </video>
    </div>

    <main
      style={{
        position: 'relative',
        height: '100vh',
        display: 'flex',
        alignItems: 'stretch',
        justifyContent: 'center',
        padding: '40px 72px 24px',
      }}
    >
      <section
        style={{
          width: 455,
          maxWidth: '100%',
          margin: 'auto 0',
        }}
      >
        {children}
      </section>

      <footer
        style={{
          position: 'absolute',
          right: 0,
          bottom: 24,
          left: 0,
          color: '#a0a3ad',
          fontSize: 12,
          lineHeight: 1.5,
          textAlign: 'center',
          whiteSpace: 'nowrap',
        }}
      >
        粤ICP备2025112253号&nbsp;&nbsp; |
        &nbsp;&nbsp;广州信计网络科技有限公司&nbsp;&nbsp; | &nbsp;&nbsp;Copyright
        © 2026 绘梦空间. All rights reserved.
      </footer>
    </main>
  </div>
);

export default AuthPageLayout;

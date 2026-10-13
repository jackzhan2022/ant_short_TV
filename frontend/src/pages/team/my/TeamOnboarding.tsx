import { TeamOutlined } from '@ant-design/icons';
import type { ReactNode } from 'react';
import { useStyles } from './TeamOnboarding.style';

const steps = [
  { title: '创建团队', description: '建立你的个人或协作空间' },
  { title: '创建项目', description: '让你的短剧想法有个起点' },
  { title: '开始创作', description: '从剧本到成片，逐步完成作品' },
];

const TeamOnboarding = ({ createAction }: { createAction: ReactNode }) => {
  const { styles } = useStyles();
  return (
    <section className={styles.surface} aria-labelledby="team-onboarding-title">
      <div className={styles.hero}>
        <div className={styles.symbol} aria-hidden="true">
          <TeamOutlined />
        </div>
        <span className={styles.eyebrow}>你的创作，从这里开始</span>
        <h1 id="team-onboarding-title" className={styles.title}>
          欢迎使用剧智创
        </h1>
        <p className={styles.subtitle}>
          创建或加入一个创作团队，开始你的短剧创作
        </p>
        <p className={styles.description}>
          团队是管理项目、素材和积分的工作空间。
          <br />
          一个人也可以创建个人团队，后续随时邀请伙伴加入。
        </p>
        <div className={styles.action}>{createAction}</div>
        <p className={styles.hint}>无需邀请成员，创建后即可开始</p>
      </div>
      <ol className={styles.steps} aria-label="开始创作的三个步骤">
        {steps.map((step, index) => (
          <li className={styles.step} key={step.title}>
            <span className={styles.number} aria-hidden="true">
              0{index + 1}
            </span>
            <div>
              <h2 className={styles.stepTitle}>{step.title}</h2>
              <p className={styles.stepDescription}>{step.description}</p>
            </div>
          </li>
        ))}
      </ol>
      <div className={styles.invitation}>
        <TeamOutlined aria-hidden="true" />
        <p>
          <strong>已有团队？</strong>
          请联系团队管理员获取邀请链接，打开链接后即可加入。
        </p>
      </div>
    </section>
  );
};

export default TeamOnboarding;

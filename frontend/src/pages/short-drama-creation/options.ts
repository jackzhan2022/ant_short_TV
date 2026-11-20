export const aspectRatioOptions = [
  { label: '16:9', value: '16:9' },
  { label: '9:16', value: '9:16' },
];

export const fileFormatOptions = [
  { label: '剧本格式', value: 'SCRIPT' },
  { label: '小说格式', value: 'NOVEL' },
];

export const scriptTypeOptions = [
  { label: '精品剧', value: 'PREMIUM_DRAMA' },
  { label: '解说漫', value: 'COMMENTARY_COMIC' },
];

export const breakdownStrengthOptions = [
  { label: '低强度', value: 'LOW' },
  { label: '中强度', value: 'MEDIUM' },
  { label: '高强度', value: 'HIGH' },
];

export const storyboardSkillOptions = [
  { label: '原分镜', value: 'V1' },
  { label: '分镜技能2.0', value: 'V2' },
  { label: '分镜技能3.0', value: 'V3' },
];

export const storyboardSkillDescriptions = {
  V1: '沿用现有分镜方式，以中近景和特写推进剧情。',
  V2: '按完整叙事片段拆分，支持 4–15 秒片段，镜头数量随剧情调整。',
  V3: '按标杆规则编排，使用9:16竖屏，每条15秒，包含全景续接与固定摄影风格。',
};

export const coverSourceOptions = [
  { label: '默认分镜首图', value: 'FIRST_FRAME' },
  { label: '上传图片', value: 'UPLOAD' },
];

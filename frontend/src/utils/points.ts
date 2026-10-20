const pointFormatter = new Intl.NumberFormat('zh-CN', {
  maximumFractionDigits: 0,
});

export const formatPoints = (value: number) =>
  pointFormatter.format(value).replace(/^-0$/, '0');

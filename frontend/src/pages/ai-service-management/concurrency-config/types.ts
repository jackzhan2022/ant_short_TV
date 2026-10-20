export type ConcurrencySettings = {
  teamTextLimit: number;
  teamImageLimit: number;
  teamVideoLimit: number;
  modelLimit: number;
  providerLimit: number;
  textWorkers: number;
  imageWorkers: number;
  videoSubmitWorkers: number;
  videoQueryWorkers: number;
};

export type ConcurrencyConfig = {
  settings: ConcurrencySettings;
  databaseMaxConnections: number;
};

export const settingMaximums: Record<keyof ConcurrencySettings, number> = {
  teamTextLimit: 10000,
  teamImageLimit: 10000,
  teamVideoLimit: 10000,
  modelLimit: 10000,
  providerLimit: 10000,
  textWorkers: 200,
  imageWorkers: 200,
  videoSubmitWorkers: 200,
  videoQueryWorkers: 200,
};

export interface ProviderFailureRate {
  provider: string;
  total: number;
  failed: number;
  failureRate: number;
}

export interface PlatformAiOperationsOverview {
  expiredClaims: number;
  retryExhausted: number;
  unpricedUsage: number;
  incompleteUsage: number;
  settlementReview: number;
  totalProviderCost: number;
  totalSettledPoints: number;
  providerFailureRates: ProviderFailureRate[];
}

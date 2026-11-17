import { request } from '@umijs/max';

export type CustomerServiceConfig = { qrCodeUrl: string };
type Response = { success: boolean; data: CustomerServiceConfig; errorMessage?: string };
const unwrap = (response: Response) => {
  if (!response.success) throw new Error(response.errorMessage || '客服配置请求失败');
  return response.data;
};

export const getCustomerService = async () => unwrap(await request<Response>('/api/public/customer-service', { skipErrorHandler: true }));
export const getCustomerServiceConfig = async () => unwrap(await request<Response>('/api/platform/customer-service', { skipErrorHandler: true }));
export const saveCustomerService = async (file: File) => {
  const data = new FormData();
  data.append('file', file);
  return unwrap(await request<Response>('/api/platform/customer-service', { method: 'PUT', data, skipErrorHandler: true }));
};
export const removeCustomerService = async () => unwrap(await request<Response>('/api/platform/customer-service', { method: 'DELETE', skipErrorHandler: true }));

// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/auth/bootstrap */
export async function bootstrap(options?: { [key: string]: any }) {
  return request<API.ApiResponseAuthBootstrapResponse>("/api/auth/bootstrap", {
    method: "GET",
    ...(options || {}),
  });
}

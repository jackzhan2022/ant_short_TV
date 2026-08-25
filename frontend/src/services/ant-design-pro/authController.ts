// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 POST /api/auth/login */
export async function login(
  body: API.LoginByMobileRequest,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseAuthSessionResponse>("/api/auth/login", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 POST /api/auth/logout */
export async function logout(options?: { [key: string]: any }) {
  return request<API.ApiResponseVoid>("/api/auth/logout", {
    method: "POST",
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 POST /api/auth/register */
export async function register(
  body: API.RegisterRequest,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseAuthSessionResponse>("/api/auth/register", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

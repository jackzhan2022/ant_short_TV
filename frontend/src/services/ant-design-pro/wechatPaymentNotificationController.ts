// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 POST /api/commercial/payments/wechat/notify */
export async function notify(body: string, options?: { [key: string]: any }) {
  return request<Record<string, any>>(
    "/api/commercial/payments/wechat/notify",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      data: body,
      ...(options || {}),
    }
  );
}

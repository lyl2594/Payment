import { http } from './request'

/**
 * 后台管理 API（JWT 自动携带，统一响应体解包见 request.js）。
 */

// ---- 看板 ----

export function fetchSummary() {
  return http.get('/api/admin/dashboard/summary')
}

export function fetchTrend(days = 7) {
  return http.get('/api/admin/dashboard/trend', { params: { days } })
}

// ---- 订单管理 ----

export function fetchOrders(params) {
  return http.get('/api/admin/orders', { params })
}

export function fetchOrderDetail(orderNo) {
  return http.get(`/api/admin/orders/${orderNo}`)
}

/** 创建补差价单：金额一律以分为单位提交 */
export function createOrder(payload) {
  return http.post('/api/admin/orders', payload)
}

export function closeOrder(orderNo) {
  return http.post(`/api/admin/orders/${orderNo}/close`)
}

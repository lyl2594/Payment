import { http } from './request'

/**
 * H5 支付相关 API（短码匿名访问，不携带 JWT）。
 * 金额权威：发起支付请求体不带任何金额参数，金额一律服务端下发。
 */

/** 凭短码获取订单信息（待支付返回完整信息；失效返回错误码 21001） */
export function fetchOrder(shortCode) {
  return http.get(`/api/open/orders/${shortCode}`)
}

/** 发起支付：返回流水号 + 收银台地址 */
export function pay(shortCode) {
  return http.post(`/api/open/s/${shortCode}/pay`, {})
}

/** 流水状态查询（H5 轮询用） */
export function fetchTxnStatus(txnNo) {
  return http.get(`/api/open/txns/${txnNo}`)
}

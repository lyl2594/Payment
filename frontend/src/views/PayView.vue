<template>
  <div class="pay-page">
    <div class="phone-card">
      <!-- 加载中 -->
      <div v-if="pageState === 'loading'" class="state-box">
        <el-skeleton :rows="4" animated />
      </div>

      <!-- 订单失效态 -->
      <div v-else-if="pageState === 'invalid'" class="state-box" data-testid="pay-invalid">
        <div class="state-icon invalid">✕</div>
        <p class="state-title">订单已失效</p>
        <p class="state-desc">订单不存在、已关闭或已超过有效期，无法完成支付<br />如有疑问请联系客服</p>
      </div>

      <!-- 支付成功页 -->
      <div v-else-if="pageState === 'success'" class="state-box" data-testid="pay-success">
        <div class="state-icon success">✓</div>
        <p class="state-title">支付成功</p>
        <p class="state-desc">补差价支付已完成</p>
        <div v-if="order" class="result-summary">
          <div class="summary-row"><span>商品</span><span>{{ order.productName }}</span></div>
          <div class="summary-row"><span>支付金额</span><span class="strong">¥ {{ yuan(order.amountCent) }}</span></div>
        </div>
      </div>

      <!-- 支付失败页（可重新支付） -->
      <div v-else-if="pageState === 'failed'" class="state-box" data-testid="pay-failed">
        <div class="state-icon failed">!</div>
        <p class="state-title">支付未完成</p>
        <p class="state-desc">{{ failReason || '支付失败，可重新发起支付' }}</p>
        <el-button type="primary" round size="large" class="wide-btn" data-testid="pay-retry" @click="startPay">
          重新支付
        </el-button>
      </div>

      <!-- 支付结果确认中 -->
      <div v-else-if="pageState === 'polling'" class="state-box" data-testid="pay-polling">
        <el-icon class="rotating" :size="42" color="#07c160"><Loading /></el-icon>
        <p class="state-title">支付结果确认中…</p>
        <p class="state-desc">请稍候，正在获取支付结果</p>
      </div>

      <!-- 待支付订单表单 -->
      <div v-else class="order-box" data-testid="pay-form">
        <div class="merchant-row">商户 · 补差价收款</div>
        <div class="amount" data-testid="pay-amount">¥ {{ yuan(order?.amountCent) }}</div>
        <div class="product" data-testid="pay-product">{{ order?.productName }}</div>
        <div v-if="order?.quotaDesc" class="meta">名额说明：{{ order.quotaDesc }}</div>
        <div v-if="order?.remark" class="meta">备注：{{ order.remark }}</div>
        <div class="meta" data-testid="pay-remain">订单剩余有效时间：{{ remainText }}</div>
        <el-button
          type="primary" size="large" round class="wide-btn"
          :loading="paying" data-testid="pay-submit" @click="startPay">
          确认支付
        </el-button>
        <div class="safe-tip">支付由模拟渠道提供 · 演示环境</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { fetchOrder, pay, fetchTxnStatus } from '../api/pay'

/**
 * H5 支付页（spec: h5-payment）：
 * 待支付订单展示信息与支付按钮；失效订单展示失效页；
 * 支付后从收银台返回时凭 txnNo 轮询流水（2s 间隔、上限 30s）至终态，展示成功/失败结果；
 * 失败可重新支付（生成新流水）。
 */
const route = useRoute()
const shortCode = route.params.shortCode

const order = ref(null)
const pageState = ref('loading') // loading / form / invalid / polling / success / failed
const failReason = ref('')
const paying = ref(false)

const POLL_INTERVAL_MS = 2000
const POLL_MAX_TIMES = 15 // 2s × 15 = 30s 上限

const remainText = computed(() => {
  const s = order.value?.remainSeconds ?? 0
  if (s <= 0) return '已过期'
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  if (h > 0) return `${h} 小时 ${m} 分钟`
  if (m > 0) return `${m} 分 ${sec} 秒`
  return `${sec} 秒`
})

function yuan(cent) {
  return ((cent ?? 0) / 100).toFixed(2)
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

onMounted(() => {
  const txnNo = route.query.txnNo
  if (txnNo) {
    // 从收银台返回：轮询该笔流水至终态后展示结果
    pollTxn(txnNo)
  } else {
    loadOrder()
  }
})

async function loadOrder() {
  try {
    const data = await fetchOrder(shortCode)
    order.value = data
    if (data.status === 'SUCCESS') {
      pageState.value = 'success'
    } else if (data.status === 'PENDING') {
      pageState.value = 'form'
    } else {
      pageState.value = 'invalid'
    }
  } catch (e) {
    // 短码无效/订单已失效（21001）
    pageState.value = 'invalid'
  }
}

async function startPay() {
  paying.value = true
  try {
    const data = await pay(shortCode)
    // 跳转模拟收银台（cashierUrl 由服务端生成并附带 shortCode）
    window.location.href = data.cashierUrl
  } catch (e) {
    // 订单失效等：切换失效态
    pageState.value = 'invalid'
  } finally {
    paying.value = false
  }
}

async function pollTxn(txnNo) {
  pageState.value = 'polling'
  for (let i = 0; i < POLL_MAX_TIMES; i++) {
    await sleep(POLL_INTERVAL_MS)
    try {
      const t = await fetchTxnStatus(txnNo)
      if (t.txnStatus === 'SUCCESS') {
        await loadOrder() // 订单状态为准，展示成功页
        return
      }
      if (t.txnStatus === 'FAILED') {
        failReason.value = t.failReason || '支付失败'
        pageState.value = 'failed'
        return
      }
    } catch (e) {
      // 单次查询失败忽略，继续轮询
    }
  }
  // 轮询超时：按订单当前状态兜底展示
  await loadOrder()
}
</script>

<style scoped>
.pay-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #e7f8ef 0%, #f2f6f4 100%);
  padding: 16px;
}
.phone-card {
  width: 100%;
  max-width: 420px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  padding: 28px 24px;
  box-sizing: border-box;
}
.state-box {
  text-align: center;
  padding: 24px 0;
}
.state-icon {
  width: 64px;
  height: 64px;
  line-height: 64px;
  border-radius: 50%;
  margin: 0 auto 16px;
  font-size: 34px;
  color: #fff;
  font-weight: 700;
}
.state-icon.success { background: #07c160; }
.state-icon.invalid { background: #c0c4cc; }
.state-icon.failed { background: #f56c6c; }
.state-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px;
}
.state-desc {
  font-size: 13px;
  color: #909399;
  margin: 0 0 20px;
  line-height: 1.7;
}
.result-summary {
  background: #f7f9f8;
  border-radius: 8px;
  padding: 12px 16px;
  text-align: left;
}
.summary-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #606266;
  padding: 6px 0;
}
.summary-row .strong { color: #303133; font-weight: 600; }
.merchant-row {
  font-size: 14px;
  color: #606266;
  margin-bottom: 12px;
}
.amount {
  font-size: 40px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 10px;
}
.product {
  font-size: 15px;
  color: #303133;
  margin-bottom: 14px;
}
.meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}
.wide-btn {
  width: 100%;
  margin-top: 20px;
}
.safe-tip {
  margin-top: 16px;
  font-size: 12px;
  color: #c0c4cc;
}
.rotating {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>

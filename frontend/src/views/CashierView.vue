<template>
  <div class="cashier-page">
    <div class="cashier-card">
      <!-- 渠道头部条 -->
      <div class="channel-bar">
        <span class="channel-logo">模拟支付</span>
        <span class="channel-tag">演示环境 · Mock Channel</span>
      </div>

      <!-- 加载中 -->
      <div v-if="pageState === 'loading'" class="state-box">
        <el-skeleton :rows="3" animated />
      </div>

      <!-- 流水不存在 -->
      <div v-else-if="pageState === 'invalid'" class="state-box" data-testid="cashier-invalid">
        <div class="state-icon invalid">✕</div>
        <p class="state-title">支付订单不存在</p>
        <p class="state-desc">流水号无效或已失效，请返回商户页重新发起支付</p>
      </div>

      <!-- 收银台操作页 -->
      <div v-else-if="pageState === 'form'" class="cashier-body" data-testid="cashier-form">
        <div class="merchant-row">补差价收款</div>
        <div class="amount" data-testid="cashier-amount">¥ {{ yuan(txn?.amountCent) }}</div>
        <div class="txn-lines">
          <div class="txn-line"><span>支付流水号</span><span>{{ txn?.txnNo }}</span></div>
          <div class="txn-line"><span>商户订单号</span><span>{{ txn?.orderNo }}</span></div>
        </div>
        <el-button
          type="primary" size="large" round class="wide-btn confirm-btn"
          :loading="processing" data-testid="cashier-confirm" @click="operate('SUCCESS')">
          确认支付
        </el-button>
        <el-button
          size="large" round class="wide-btn fail-btn"
          :disabled="processing" data-testid="cashier-fail" @click="operate('FAILED')">
          模拟支付失败
        </el-button>
        <div class="tip">本页面为演示用模拟收银台，不代表任何真实支付渠道</div>
      </div>

      <!-- 结果确认中 -->
      <div v-else-if="pageState === 'processing'" class="state-box" data-testid="cashier-processing">
        <el-icon class="rotating" :size="42" color="#07c160"><Loading /></el-icon>
        <p class="state-title">支付处理中…</p>
        <p class="state-desc">正在确认支付结果，请勿关闭页面</p>
      </div>

      <!-- 支付成功 -->
      <div v-else-if="pageState === 'success'" class="state-box" data-testid="cashier-success">
        <div class="state-icon success">✓</div>
        <p class="state-title">付款成功</p>
        <p class="state-desc">¥ {{ yuan(txn?.amountCent) }} · {{ txn?.txnNo }}</p>
        <el-button v-if="shortCode" type="primary" round size="large" class="wide-btn" data-testid="cashier-back" @click="backToMerchant">
          返回商户页
        </el-button>
      </div>

      <!-- 支付失败 -->
      <div v-else-if="pageState === 'failed'" class="state-box" data-testid="cashier-failed">
        <div class="state-icon failed">!</div>
        <p class="state-title">付款失败</p>
        <p class="state-desc">{{ txn?.failReason || '支付未完成' }}</p>
        <el-button v-if="shortCode" type="primary" round size="large" class="wide-btn" data-testid="cashier-back" @click="backToMerchant">
          返回商户页
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { fetchTxnStatus } from '../api/pay'
import { http } from '../api/request'

/**
 * 模拟收银台（spec: mock-payment-channel）：
 * 第三方风格 UI 展示流水号与应付金额（服务端下发），提供"确认支付/模拟支付失败"两种操作；
 * 操作触发 /api/mock/channel/pay → 渠道侧签名并回环投递异步回调 → 轮询流水至终态。
 */
const route = useRoute()
const router = useRouter()
const txnNo = route.params.txnNo
const shortCode = route.query.shortCode

const txn = ref(null)
const pageState = ref('loading') // loading / form / processing / success / failed / invalid
const processing = ref(false)

const POLL_INTERVAL_MS = 2000
const POLL_MAX_TIMES = 15

function yuan(cent) {
  return ((cent ?? 0) / 100).toFixed(2)
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

onMounted(loadTxn)

async function loadTxn() {
  try {
    const t = await fetchTxnStatus(txnNo)
    txn.value = t
    if (t.txnStatus === 'SUCCESS') {
      pageState.value = 'success' // 重复进入：直接展示已支付结果（渠道侧幂等）
    } else if (t.txnStatus === 'FAILED') {
      pageState.value = 'failed'
    } else {
      pageState.value = 'form'
    }
  } catch (e) {
    pageState.value = 'invalid'
  }
}

async function operate(result) {
  processing.value = true
  pageState.value = 'processing'
  try {
    // 渠道侧受理用户操作：生成渠道交易号、签名并回环投递异步回调
    await http.post('/api/mock/channel/pay', { txnNo, result })
    await pollUntilFinal()
  } catch (e) {
    pageState.value = 'form'
  } finally {
    processing.value = false
  }
}

async function pollUntilFinal() {
  for (let i = 0; i < POLL_MAX_TIMES; i++) {
    await sleep(POLL_INTERVAL_MS)
    try {
      const t = await fetchTxnStatus(txnNo)
      txn.value = t
      if (t.txnStatus === 'SUCCESS') {
        pageState.value = 'success'
        return
      }
      if (t.txnStatus === 'FAILED') {
        pageState.value = 'failed'
        return
      }
    } catch (e) {
      // 单次查询失败忽略，继续轮询
    }
  }
  // 超时：回到操作页（异步通知可能延迟）
  pageState.value = 'form'
}

function backToMerchant() {
  // 跳回商户 H5 结果页：携带 txnNo，H5 侧轮询流水终态后展示成功/失败结果
  router.push(`/pay/${shortCode}?txnNo=${txnNo}`)
}
</script>

<style scoped>
.cashier-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f6f8;
  padding: 16px;
}
.cashier-card {
  width: 100%;
  max-width: 420px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  box-sizing: border-box;
}
.channel-bar {
  background: #07c160;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
}
.channel-logo {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 2px;
}
.channel-tag {
  font-size: 12px;
  opacity: 0.85;
}
.cashier-body,
.state-box {
  padding: 28px 24px;
}
.state-box {
  text-align: center;
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
}
.merchant-row {
  text-align: center;
  font-size: 14px;
  color: #606266;
  margin-bottom: 10px;
}
.amount {
  text-align: center;
  font-size: 42px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 20px;
}
.txn-lines {
  background: #f7f8fa;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 24px;
}
.txn-line {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  padding: 5px 0;
  word-break: break-all;
}
.txn-line span:first-child {
  flex-shrink: 0;
  margin-right: 12px;
}
.wide-btn {
  width: 100%;
  margin: 0 0 12px;
}
.confirm-btn {
  background: #07c160;
  border-color: #07c160;
}
.fail-btn {
  color: #f56c6c;
  border-color: #f56c6c;
}
.tip {
  text-align: center;
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 8px;
}
.rotating {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>

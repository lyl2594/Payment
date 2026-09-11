<template>
  <el-container class="admin-layout">
    <el-header class="admin-header">
      <span class="brand">补差价运营后台</span>
      <span class="spacer" />
      <span class="user">{{ user?.displayName || user?.username }}</span>
      <el-button link type="danger" data-testid="logout-btn" @click="onLogout">退出登录</el-button>
    </el-header>
    <el-main>
      <!-- 汇总指标卡 -->
      <el-row :gutter="12" class="stat-row">
        <el-col v-for="card in statCards" :key="card.label" :span="6" style="margin-bottom: 12px">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">{{ card.label }}</div>
            <div class="stat-value" :data-testid="card.testId">{{ card.value }}</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 收款趋势 -->
      <el-card shadow="never" class="section-card">
        <template #header>
          <div class="section-head">
            <span>近 {{ trendDays }} 日收款趋势</span>
            <el-button size="small" data-testid="refresh-btn" @click="refreshAll">刷新</el-button>
          </div>
        </template>
        <div ref="trendChartRef" class="trend-chart" />
      </el-card>

      <!-- 订单列表 -->
      <el-card shadow="never" class="section-card">
        <template #header>
          <div class="section-head">
            <span>订单列表</span>
            <el-button type="primary" data-testid="create-order-btn" @click="openCreate">创建补差价单</el-button>
          </div>
        </template>

        <el-form inline class="filter-form">
          <el-form-item label="状态">
            <el-select v-model="filters.status" clearable style="width: 130px" data-testid="filter-status" @change="loadOrders">
              <el-option label="待支付" value="PENDING" />
              <el-option label="已支付" value="SUCCESS" />
              <el-option label="已关闭" value="CLOSED" />
            </el-select>
          </el-form-item>
          <el-form-item label="原订单号">
            <el-input v-model="filters.originalOrderNo" clearable placeholder="按原订单号归集" style="width: 200px"
              data-testid="filter-original" @keyup.enter="loadOrders" @clear="loadOrders" />
          </el-form-item>
          <el-form-item label="关键词">
            <el-input v-model="filters.keyword" clearable placeholder="订单号/商品" style="width: 180px"
              @keyup.enter="loadOrders" @clear="loadOrders" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" plain data-testid="filter-search" @click="loadOrders">查询</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="orders" v-loading="ordersLoading" size="default" data-testid="order-table">
          <el-table-column prop="orderNo" label="订单号" width="180" />
          <el-table-column prop="originalOrderNo" label="原订单号" width="180" />
          <el-table-column prop="productName" label="商品" min-width="150" show-overflow-tooltip />
          <el-table-column label="补差金额" width="110">
            <template #default="{ row }">¥ {{ yuan(row.amountCent) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" data-testid="order-status">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="170">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="paidAt" label="支付时间" width="170">
            <template #default="{ row }">{{ row.paidAt ? fmtTime(row.paidAt) : '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :data-testid="'detail-' + row.orderNo" @click="openDetail(row)">详情</el-button>
              <el-button v-if="row.status === 'PENDING'" link type="danger" :data-testid="'close-' + row.orderNo"
                @click="onClose(row)">关闭</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="pager" background layout="total, prev, pager, next"
          :total="total" :page-size="pageSize" :current-page="page"
          @current-change="(p) => { page = p; loadOrders() }" />
      </el-card>
    </el-main>

    <!-- 建单弹窗 -->
    <el-dialog v-model="createVisible" title="创建补差价单" width="520px">
      <el-form :model="createForm" label-width="110px">
        <el-form-item label="原订单号" required>
          <el-input v-model="createForm.originalOrderNo" placeholder="必填，系统不做存在性校验" data-testid="create-original" />
        </el-form-item>
        <el-form-item label="商品名称" required>
          <el-input v-model="createForm.productName" data-testid="create-product" />
        </el-form-item>
        <el-form-item label="补差金额(元)" required>
          <el-input-number v-model="createForm.amountYuan" :min="0.01" :precision="2" :step="1" style="width: 180px"
            data-testid="create-amount" />
        </el-form-item>
        <el-form-item label="原订单金额(元)">
          <el-input-number v-model="createForm.originalAmountYuan" :min="0" :precision="2" style="width: 180px" />
        </el-form-item>
        <el-form-item label="原商品名称">
          <el-input v-model="createForm.originalProduct" />
        </el-form-item>
        <el-form-item label="名额说明">
          <el-input v-model="createForm.quotaDesc" placeholder="仅描述信息，非限量库存" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" />
        </el-form-item>
        <el-form-item label="有效期(小时)">
          <el-input-number v-model="createForm.expireHours" :min="1" :max="720" style="width: 180px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" data-testid="create-submit" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 建单成功结果 -->
    <el-dialog v-model="createdVisible" title="创建成功" width="480px">
      <el-result icon="success" title="补差价单已创建">
        <template #sub-title>
          <div class="created-info">
            <p>订单号：{{ createdResp?.orderNo }}</p>
            <p>支付短链：<el-link type="primary" :href="createdResp?.shortUrl" target="_blank" data-testid="created-short-url">
              {{ createdResp?.shortUrl }}</el-link></p>
            <p class="tip">请复制短链发给用户，用户打开即可完成支付</p>
          </div>
        </template>
        <template #extra>
          <el-button type="primary" @click="copyShortUrl">复制短链</el-button>
        </template>
      </el-result>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="订单详情" size="560px">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="1" border size="small">
          <el-descriptions-item label="订单号">{{ detail.order.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detail.order.status)">{{ statusText(detail.order.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="商品">{{ detail.order.productName }}</el-descriptions-item>
          <el-descriptions-item label="补差金额">¥ {{ yuan(detail.order.amountCent) }}</el-descriptions-item>
          <el-descriptions-item label="名额说明">{{ detail.order.quotaDesc || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detail.order.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="支付短链">
            <el-link type="primary" :href="detail.order.orderNo ? shortUrlOf(detail) : ''" target="_blank">
              {{ shortUrlOf(detail) }}</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="有效期至">{{ fmtTime(detail.order.expireAt) }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detail.order.createdBy }}</el-descriptions-item>
          <el-descriptions-item label="原订单号">{{ detail.originalOrderNo }}</el-descriptions-item>
          <el-descriptions-item label="原订单金额">
            {{ detail.originalAmountCent != null ? '¥ ' + yuan(detail.originalAmountCent) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="原商品名称">{{ detail.originalProduct || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="txn-title">支付流水（每次发起一条）</div>
        <el-table v-if="detail" :data="detail.txns" size="small">
          <el-table-column prop="txnNo" label="流水号" min-width="160" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="txnTagType(row.status)">{{ txnText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="90">
            <template #default="{ row }">¥ {{ yuan(row.amountCent) }}</template>
          </el-table-column>
          <el-table-column prop="failReason" label="失败原因" min-width="120">
            <template #default="{ row }">{{ row.failReason || '-' }}</template>
          </el-table-column>
          <el-table-column prop="successAt" label="成功时间" width="110">
            <template #default="{ row }">{{ row.successAt ? fmtTime(row.successAt) : '-' }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </el-container>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCurrentUser, logout } from '../api/auth'
import { fetchSummary, fetchTrend, fetchOrders, fetchOrderDetail, createOrder, closeOrder } from '../api/admin'

/**
 * 后台管理页（spec: payment-dashboard）：汇总指标卡 + 近 7 日趋势图 + 订单列表
 * （状态/原订单号/关键词筛选、分页）+ 建单表单（原订单号必填）+ 详情抽屉（含原订单信息与流水）+ 关单。
 */
const router = useRouter()
const user = getCurrentUser()

// ---- 看板 ----
const summary = ref(null)
const trendDays = ref(7)
const trendChartRef = ref(null)
let chart = null

const statCards = computed(() => {
  const s = summary.value
  return [
    { label: '累计收款(元)', value: yuan(s?.totalAmountCent), testId: 'stat-total-amount' },
    { label: '今日收款(元)', value: yuan(s?.todayAmountCent), testId: 'stat-today-amount' },
    { label: '订单总数', value: s?.totalOrders ?? 0, testId: 'stat-total-orders' },
    { label: '待支付', value: s?.pendingCount ?? 0, testId: 'stat-pending' },
    { label: '已支付', value: s?.successCount ?? 0, testId: 'stat-success' },
    { label: '已关闭', value: s?.closedCount ?? 0, testId: 'stat-closed' },
    { label: '支付成功率', value: (s?.successRate ?? 0) + '%', testId: 'stat-rate' }
  ]
})

async function loadSummary() {
  summary.value = await fetchSummary()
}

async function loadTrend() {
  const data = await fetchTrend(trendDays.value)
  await nextTick()
  renderTrend(data.points)
}

function renderTrend(points) {
  if (!chart) {
    chart = echarts.init(trendChartRef.value)
  }
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['收款金额(元)', '成交订单数'] },
    grid: { left: 48, right: 48, top: 40, bottom: 28 },
    xAxis: { type: 'category', data: points.map((p) => p.date.slice(5)) },
    yAxis: [
      { type: 'value', name: '金额(元)' },
      { type: 'value', name: '订单数', minInterval: 1 }
    ],
    series: [
      { name: '收款金额(元)', type: 'bar', barMaxWidth: 32, data: points.map((p) => p.amountCent / 100) },
      { name: '成交订单数', type: 'line', yAxisIndex: 1, smooth: true, data: points.map((p) => p.orderCount) }
    ]
  })
}

function onResize() {
  chart?.resize()
}

function refreshAll() {
  loadSummary()
  loadTrend()
  loadOrders()
}

// ---- 订单列表 ----
const orders = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const ordersLoading = ref(false)
const filters = reactive({ status: '', originalOrderNo: '', keyword: '' })

async function loadOrders() {
  ordersLoading.value = true
  try {
    const data = await fetchOrders({
      page: page.value,
      size: pageSize,
      status: filters.status || undefined,
      originalOrderNo: filters.originalOrderNo || undefined,
      keyword: filters.keyword || undefined
    })
    orders.value = data.records
    total.value = data.total
  } finally {
    ordersLoading.value = false
  }
}

// ---- 建单 ----
const createVisible = ref(false)
const creating = ref(false)
const createdVisible = ref(false)
const createdResp = ref(null)
const createForm = reactive({
  originalOrderNo: '',
  productName: '',
  amountYuan: null,
  originalAmountYuan: null,
  originalProduct: '',
  quotaDesc: '',
  remark: '',
  expireHours: 48
})

function openCreate() {
  createForm.originalOrderNo = ''
  createForm.productName = ''
  createForm.amountYuan = null
  createForm.originalAmountYuan = null
  createForm.originalProduct = ''
  createForm.quotaDesc = ''
  createForm.remark = ''
  createForm.expireHours = 48
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.originalOrderNo) {
    ElMessage.warning('原订单号为必填项')
    return
  }
  if (!createForm.productName) {
    ElMessage.warning('商品名称为必填项')
    return
  }
  if (!createForm.amountYuan || createForm.amountYuan <= 0) {
    ElMessage.warning('补差金额必须大于 0')
    return
  }
  creating.value = true
  try {
    // 金额以分提交：元 × 100 取整，杜绝浮点误差
    createdResp.value = await createOrder({
      originalOrderNo: createForm.originalOrderNo,
      productName: createForm.productName,
      amountCent: Math.round(createForm.amountYuan * 100),
      originalAmountCent: createForm.originalAmountYuan != null ? Math.round(createForm.originalAmountYuan * 100) : null,
      originalProduct: createForm.originalProduct || null,
      quotaDesc: createForm.quotaDesc || null,
      remark: createForm.remark || null,
      expireHours: createForm.expireHours
    })
    createVisible.value = false
    createdVisible.value = true
    refreshAll()
  } finally {
    creating.value = false
  }
}

async function copyShortUrl() {
  const text = createdResp.value?.shortUrl || ''
  // 路径 1：现代 Clipboard API（仅安全上下文 HTTPS/localhost 且页面有焦点时可用）
  if (navigator.clipboard?.writeText && document.hasFocus()) {
    try {
      await navigator.clipboard.writeText(text)
      ElMessage.success('短链已复制')
      return
    } catch (e) {
      // 降级到路径 2
    }
  }
  // 路径 2：隐藏 textarea + execCommand（兼容 HTTP/IP 访问与失焦场景）
  try {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.focus()
    ta.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    if (!ok) throw new Error('execCommand copy failed')
    ElMessage.success('短链已复制')
  } catch (e) {
    ElMessage.warning('复制失败，请手动复制')
  }
}

// ---- 详情 ----
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await fetchOrderDetail(row.orderNo)
  } finally {
    detailLoading.value = false
  }
}

function shortUrlOf(detail) {
  return window.location.origin + '/pay/' + (detail?.order?.shortCode || '')
}

// ---- 关单 ----
async function onClose(row) {
  await ElMessageBox.confirm(`确认关闭订单 ${row.orderNo}？关闭后支付短链将失效`, '关闭订单', {
    confirmButtonText: '确认关闭',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await closeOrder(row.orderNo)
  ElMessage.success('订单已关闭')
  refreshAll()
}

function onLogout() {
  logout()
  router.push('/login')
}

// ---- 工具 ----
function yuan(cent) {
  return ((cent ?? 0) / 100).toFixed(2)
}

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

function statusText(s) {
  return { PENDING: '待支付', SUCCESS: '已支付', CLOSED: '已关闭' }[s] || s
}

function statusTagType(s) {
  return { PENDING: 'warning', SUCCESS: 'success', CLOSED: 'info' }[s] || 'info'
}

function txnText(s) {
  return { CREATED: '待支付', SUCCESS: '成功', FAILED: '失败' }[s] || s
}

function txnTagType(s) {
  return { CREATED: 'warning', SUCCESS: 'success', FAILED: 'danger' }[s] || 'info'
}

onMounted(() => {
  loadSummary()
  loadTrend()
  loadOrders()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
  background: #f5f7fa;
}
.admin-header {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}
.brand {
  font-weight: 600;
  color: #303133;
}
.spacer {
  flex: 1;
}
.user {
  color: #606266;
}
.stat-row {
  margin-top: 4px;
}
.stat-card {
  text-align: left;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
}
.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
}
.section-card {
  margin-bottom: 16px;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  color: #303133;
}
.trend-chart {
  width: 100%;
  height: 300px;
}
.filter-form {
  margin-bottom: 4px;
}
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
.created-info {
  text-align: left;
  font-size: 13px;
  color: #606266;
}
.created-info .tip {
  color: #909399;
  font-size: 12px;
}
.txn-title {
  font-weight: 600;
  color: #303133;
  margin: 18px 0 10px;
}
</style>

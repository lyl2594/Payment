import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../api/auth'

// 四个路由：登录 / 后台管理 / H5 支付页 / 模拟收银台
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue')
    },
    {
      path: '/admin',
      name: 'admin',
      component: () => import('../views/AdminView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/pay/:shortCode',
      name: 'pay',
      component: () => import('../views/PayView.vue')
    },
    {
      path: '/cashier/:txnNo',
      name: 'cashier',
      component: () => import('../views/CashierView.vue')
    },
    {
      path: '/',
      redirect: '/admin'
    }
  ]
})

// 路由守卫：/admin 需登录；已登录访问 /login 直达后台
router.beforeEach((to) => {
  if (to.meta.requiresAuth && !getToken()) {
    return { name: 'login' }
  }
  if (to.name === 'login' && getToken()) {
    return { name: 'admin' }
  }
  return true
})

export default router

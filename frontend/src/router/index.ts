import ViolationsAdmin from '../views/admin/ViolationsAdmin.vue'
import UsersAdmin from '../views/admin/UsersAdmin.vue'
import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import Login from '../views/Login.vue'
import Profile from '../views/Profile.vue'
import SeatsMap from '../views/SeatsMap.vue'
import MyReservations from '../views/MyReservations.vue'
import ReservationBooks from '../views/ReservationBooks.vue'
import Dashboard from '../views/Dashboard.vue'
import ForgotPassword from '../views/ForgotPassword.vue'
import Groups from '../views/Groups.vue'
import BookDetail from '../views/BookDetail.vue'
import Notices from '../views/Notices.vue'
import Borrows from '../views/Borrows.vue'
import Favorites from '../views/Favorites.vue'
import Notifications from '../views/Notifications.vue'
import Feedback from '../views/Feedback.vue'
import SeatsAdmin from '../views/admin/SeatsAdmin.vue'
import ReservationsAdmin from '../views/admin/ReservationsAdmin.vue'
import BooksAdmin from '../views/admin/BooksAdmin.vue'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: Home },  // 首页允许匿名访问
    { path: '/login', name: 'login', component: Login },
    { path: '/forgot-password', name: 'forgot', component: ForgotPassword },
    { path: '/notices', name: 'notices', component: Notices },  // 公告通知允许匿名访问
    { path: '/checkin', name: 'checkin', component: () => import('../views/CheckIn.vue') },  // 扫码签到页面（允许匿名访问，但需要登录才能签到）
    { path: '/profile', name: 'profile', component: Profile, meta: { requiresAuth: true } },
    { path: '/seats', redirect: '/seats-map' },  // 重定向到可视化选座
    { path: '/seats-map', name: 'seatsMap', component: SeatsMap },  // 可视化选座允许匿名访问（预约需要登录）
    { path: '/my-reservations', name: 'myReservations', component: MyReservations, meta: { requiresAuth: true } },  // 管理员会看到所有预约
    { path: '/reservation-books', name: 'reservationBooks', component: ReservationBooks, meta: { requiresAuth: true } },  // 预约关联书籍
    { path: '/dashboard', name: 'dashboard', component: Dashboard, meta: { requiresAdmin: true } },  // 看板仅管理员可访问
    { path: '/groups', name: 'groups', component: Groups, meta: { requiresAuth: true } },
    { path: '/borrows', name: 'borrows', component: Borrows, meta: { requiresAuth: true } },
    { path: '/favorites', name: 'favorites', component: Favorites, meta: { requiresAuth: true } },
    { path: '/notifications', name: 'notifications', component: Notifications, meta: { requiresAuth: true } },
    { path: '/book-operation', name: 'bookOperation', component: () => import('../views/BookOperation.vue'), meta: { requiresAuth: true } },
    { path: '/feedback', name: 'feedback', component: Feedback, meta: { requiresAuth: true } },
    { path: '/book/:id', name: 'bookDetail', component: BookDetail },
    { path: '/admin/violations', name: 'adminViolations', component: ViolationsAdmin, meta: { requiresAdmin: true } },
    { path: '/admin/users', name: 'adminUsers', component: UsersAdmin, meta: { requiresAdmin: true } },
    { path: '/admin/seats', name: 'adminSeats', component: SeatsAdmin, meta: { requiresAdmin: true } },
    { path: '/admin/reservations', name: 'adminReservations', component: ReservationsAdmin, meta: { requiresAdmin: true } },
    { path: '/admin/books', name: 'adminBooks', component: BooksAdmin, meta: { requiresAdmin: true } },
    { path: '/admin/notices', name: 'adminNotices', component: () => import('../views/admin/NoticesAdmin.vue'), meta: { requiresAdmin: true } },
    { path: '/admin/borrows', name: 'adminBorrows', component: () => import('../views/admin/BorrowsAdmin.vue'), meta: { requiresAdmin: true } },
    { path: '/admin/feedbacks', name: 'adminFeedbacks', component: () => import('../views/admin/FeedbacksAdmin.vue'), meta: { requiresAdmin: true } },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  // 管理员的反馈类通知在「反馈管理」右上角面板处理，不需要独立「我的通知」页
  if (to.name === 'notifications' && auth.isAuthenticated && auth.isAdmin) {
    return { name: 'adminFeedbacks' }
  }
  if ((to.meta as any).requiresAdmin && !auth.isAdmin) {
    return { name: 'home' }
  }
  // 如果管理员访问首页，自动重定向到数据看板
  if (to.name === 'home' && auth.isAuthenticated && auth.isAdmin) {
    return { name: 'dashboard' }
  }
})

export default router



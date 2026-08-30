/**
 * 全局右键用户菜单状态
 * 任何视图只需调用 openUserMenu(e, {userId, userName}) 即可弹出统一菜单（查看主页/私聊/关注/邀请组队）
 */
import { reactive } from 'vue'

export const userMenuState = reactive({
  visible: false,
  x: 0,
  y: 0,
  userId: null,
  userName: '',
  username: ''
})

export function openUserMenu(e, { userId, userName }) {
  userMenuState.userId = userId
  userMenuState.userName = userName || ''
  userMenuState.username = ''
  userMenuState.x = Math.min(e.clientX, window.innerWidth - 200)
  userMenuState.y = Math.min(e.clientY, window.innerHeight - 200)
  userMenuState.visible = true
}

export function closeUserMenu() {
  userMenuState.visible = false
}

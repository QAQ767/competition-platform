import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import {
  Aim,
  ArrowRight,
  ArrowUp,
  Bell,
  Calendar,
  ChatDotRound,
  CircleCheck,
  CircleCheckFilled,
  Clock,
  Connection,
  Document,
  DocumentChecked,
  Edit,
  EditPen,
  InfoFilled,
  MagicStick,
  Medal,
  Menu,
  Message,
  Monitor,
  OfficeBuilding,
  Plus,
  Reading,
  Right,
  Search,
  Setting,
  Star,
  StarFilled,
  Timer,
  TopRight,
  Trophy,
  User
} from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import './styles/theme.css'
import PageHeading from './components/PageHeading.vue'
import UiLabel from './components/UiLabel.vue'
import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

for (const [key, component] of Object.entries({
  Aim,
  ArrowRight,
  ArrowUp,
  Bell,
  Calendar,
  ChatDotRound,
  CircleCheck,
  CircleCheckFilled,
  Clock,
  Connection,
  Document,
  DocumentChecked,
  Edit,
  EditPen,
  InfoFilled,
  MagicStick,
  Medal,
  Menu,
  Message,
  Monitor,
  OfficeBuilding,
  Plus,
  Reading,
  Right,
  Search,
  Setting,
  Star,
  StarFilled,
  Timer,
  TopRight,
  Trophy,
  User
})) {
  app.component(key, component)
}

app.component('PageHeading', PageHeading)
app.component('UiLabel', UiLabel)
app.mount('#app')

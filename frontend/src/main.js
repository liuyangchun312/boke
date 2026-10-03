import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { reveal } from './directives/reveal'
import './styles.css'
import './website-polish.css'
import './rural-theme.css'
import './interactions.css'
import '@fontsource/noto-serif-sc/400.css'
import '@fontsource/noto-serif-sc/500.css'
import '@fontsource/noto-sans-sc/400.css'
import '@fontsource/noto-sans-sc/600.css'

createApp(App).directive('reveal', reveal).use(router).mount('#app')

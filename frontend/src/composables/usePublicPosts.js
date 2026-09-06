import { computed, onMounted, ref } from 'vue'
import { fetchAllPosts } from '../services/api'
import { sortPosts } from '../utils/blog'

export const usePublicPosts = () => {
  const posts = ref([])
  const loading = ref(true)
  const error = ref('')
  let requestId = 0

  const categories = computed(() => [...new Set(posts.value.map((post) => post.category).filter(Boolean))])
  const tags = computed(() => [...new Set(posts.value.flatMap((post) => post.tags).filter(Boolean))])

  const load = async () => {
    const current = ++requestId
    loading.value = true
    error.value = ''
    try {
      const result = await fetchAllPosts()
      if (current === requestId) posts.value = sortPosts(result || [])
    } catch (cause) {
      if (current === requestId) error.value = cause?.message || '暂时无法读取文章，请稍后重试。'
    } finally {
      if (current === requestId) loading.value = false
    }
  }

  onMounted(load)
  return { posts, categories, tags, loading, error, load }
}

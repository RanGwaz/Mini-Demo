import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

export type InfiniteLoadFn = () => boolean | Promise<boolean>

export function useInfiniteScroll(loadFn: InfiniteLoadFn) {
  const sentinel = ref<HTMLElement | null>(null)
  const loading = ref(false)
  const finished = ref(false)
  let observer: IntersectionObserver | null = null

  async function runLoad() {
    if (loading.value || finished.value) return
    loading.value = true
    try {
      const hasMore = await loadFn()
      if (!hasMore) finished.value = true
    } finally {
      loading.value = false
    }
  }

  const handleIntersect: IntersectionObserverCallback = ([entry]) => {
    if (!entry?.isIntersecting) return
    void runLoad()
  }

  function observeCurrentSentinel() {
    observer?.disconnect()
    if (!sentinel.value) return
    observer = new IntersectionObserver(handleIntersect, {
      rootMargin: '200px',
      threshold: 0.01,
    })
    observer.observe(sentinel.value)
  }

  function resetInfiniteState() {
    finished.value = false
    loading.value = false
    void nextTick(observeCurrentSentinel)
  }

  watch(sentinel, () => {
    observeCurrentSentinel()
  })

  onMounted(observeCurrentSentinel)
  onUnmounted(() => observer?.disconnect())

  return { sentinel, loading, finished, runLoad, resetInfiniteState }
}

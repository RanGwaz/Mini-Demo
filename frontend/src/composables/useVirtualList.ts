import { computed, onBeforeUnmount, ref } from 'vue'

export interface VirtualListOptions {
  buffer?: number // 上下缓冲区数量（卡片数）
  containerSelector?: string // 滚动容器选择器
}

export interface VirtualListState {
  visiblePosts: any[]
  offsetY: number
  totalHeight: number
  startIndex: number
  endIndex: number
}

/**
 * 虚拟滚动 composable
 * 支持动态高度卡片，通过 ResizeObserver 缓存实际高度
 */
export function useVirtualList(posts: any[], options: VirtualListOptions = {}) {
  const { buffer = 5, containerSelector = '.virtual-scroll-container' } = options

  // 状态
  const containerRef = ref<HTMLElement | null>(null)
  const contentRef = ref<HTMLElement | null>(null)
  const scrollTop = ref(0)
  const containerHeight = ref(0)

  // 高度缓存：postId -> 实际高度
  const heightCache = new Map<number, number>()

  // 计算可见范围
  const visibleRange = computed(() => {
    if (!containerHeight.value || posts.length === 0) {
      return { startIndex: 0, endIndex: 0, offsetY: 0 }
    }

    let accumulatedHeight = 0
    let startIndex = 0
    let endIndex = 0
    let offsetY = 0

    // 找到起始索引
    for (let i = 0; i < posts.length; i++) {
      const h = getPostHeight(posts[i])
      if (accumulatedHeight + h >= scrollTop.value) {
        startIndex = Math.max(0, i - buffer)
        offsetY = getOffsetY(startIndex)
        break
      }
      accumulatedHeight += h
    }

    // 找到结束索引
    accumulatedHeight = getOffsetY(startIndex)
    for (let i = startIndex; i < posts.length; i++) {
      accumulatedHeight += getPostHeight(posts[i])
      if (accumulatedHeight >= scrollTop.value + containerHeight.value) {
        endIndex = Math.min(posts.length - 1, i + buffer)
        break
      }
    }

    return { startIndex, endIndex: Math.max(startIndex, endIndex), offsetY }
  })

  const visiblePosts = computed(() => {
    const { startIndex, endIndex } = visibleRange.value
    return posts.slice(startIndex, endIndex + 1)
  })

  const totalHeight = computed(() => {
    return posts.reduce((sum, post) => sum + getPostHeight(post), 0)
  })

  const offsetY = computed(() => visibleRange.value.offsetY)

  // 获取单个卡片高度（从缓存或使用默认值）
  function getPostHeight(post: any): number {
    if (heightCache.has(post.id)) {
      return heightCache.get(post.id)!
    }
    // 默认估算高度：200px（可根据实际调整）
    return 200
  }

  // 计算从 startIndex 到顶部的偏移量
  function getOffsetY(startIndex: number): number {
    let offset = 0
    for (let i = 0; i < startIndex; i++) {
      offset += getPostHeight(posts[i])
    }
    return offset
  }

  // 更新卡片实际高度
  function updatePostHeight(postId: number, height: number) {
    heightCache.set(postId, height)
  }

  // 处理滚动事件
  function handleScroll(e: Event) {
    const target = e.target as HTMLElement
    scrollTop.value = target.scrollTop
  }

  // 初始化容器
  function initContainer() {
    const el = document.querySelector(containerSelector) as HTMLElement
    if (el) {
      containerRef.value = el
      containerHeight.value = el.clientHeight
      el.addEventListener('scroll', handleScroll, { passive: true })

      // 监听容器高度变化
      const resizeObs = new ResizeObserver(() => {
        containerHeight.value = el.clientHeight
      })
      resizeObs.observe(el)

      return () => {
        el.removeEventListener('scroll', handleScroll)
        resizeObs.disconnect()
      }
    }
  }

  // 清理
  onBeforeUnmount(() => {
    if (containerRef.value) {
      containerRef.value.removeEventListener('scroll', handleScroll)
    }
  })

  // 重置缓存
  function resetHeightCache() {
    heightCache.clear()
  }

  return {
    containerRef,
    contentRef,
    visiblePosts,
    offsetY,
    totalHeight,
    startIndex: computed(() => visibleRange.value.startIndex),
    endIndex: computed(() => visibleRange.value.endIndex),
    getPostHeight,
    updatePostHeight,
    resetHeightCache,
    initContainer,
  }
}

import { computed, ref, type Ref } from 'vue'

export function useVideoLoader<T>(source: Ref<T[]>, pageSize = 9) {
  const visibleItems = ref<T[]>([]) as Ref<T[]>
  const cursor = ref(0)
  const hasLoaded = ref(false)

  const total = computed(() => source.value.length)

  async function loadNextPage() {
    const nextItems = source.value.slice(cursor.value, cursor.value + pageSize)
    visibleItems.value.push(...nextItems)
    cursor.value += nextItems.length
    hasLoaded.value = true
    return cursor.value < total.value
  }

  async function resetAndLoad() {
    visibleItems.value = []
    cursor.value = 0
    hasLoaded.value = false
    if (total.value === 0) {
      hasLoaded.value = true
      return false
    }
    return loadNextPage()
  }

  return { visibleItems, cursor, hasLoaded, total, loadNextPage, resetAndLoad }
}

import { ref, watch } from 'vue'

const KEY_HIDDEN = 'music-web-table-hidden-cols'
const KEY_ORDER = 'music-web-table-col-order'

function loadHidden() {
  try {
    const raw = localStorage.getItem(KEY_HIDDEN)
    if (raw) return new Set(JSON.parse(raw))
  } catch {  }
  return null
}

function loadOrder() {
  try {
    const raw = localStorage.getItem(KEY_ORDER)
    if (raw) return JSON.parse(raw)
  } catch {  }
  return null
}

function resolveColumns(cols) {
  return cols && typeof cols === 'object' && 'value' in cols ? cols.value : cols
}

export function useColumnPreferences(allColumns) {
  const hiddenKeys = ref(loadHidden())
  const columnOrder = ref(loadOrder())

  function cols() { return resolveColumns(allColumns) }

  watch(hiddenKeys, (val) => {
    if (val === null) localStorage.removeItem(KEY_HIDDEN)
    else localStorage.setItem(KEY_HIDDEN, JSON.stringify([...val]))
  }, { deep: true })

  watch(columnOrder, (val) => {
    if (val === null) localStorage.removeItem(KEY_ORDER)
    else localStorage.setItem(KEY_ORDER, JSON.stringify(val))
  }, { deep: true })

    if (hiddenKeys.value === null) {
    hiddenKeys.value = new Set(cols().filter((c) => !c.defaultVisible).map((c) => c.key))
  }

    function syncOrder(keys) {
    const saved = columnOrder.value || cols().map((c) => c.key)
    const merged = [...saved]
        for (const k of keys) {
      if (!merged.includes(k)) merged.push(k)
    }
        return merged.filter((k) => keys.includes(k))
  }

  const allKeys = cols().map((c) => c.key)
  columnOrder.value = syncOrder(allKeys)

  function isVisible(key) {
    return !hiddenKeys.value.has(key)
  }

  function toggle(key) {
    const next = new Set(hiddenKeys.value)
    next.has(key) ? next.delete(key) : next.add(key)
    hiddenKeys.value = next
  }

  function moveUp(key) {
    const order = [...columnOrder.value]
    const i = order.indexOf(key)
    if (i > 0) {
      ;[order[i - 1], order[i]] = [order[i], order[i - 1]]
      columnOrder.value = order
    }
  }

  function moveDown(key) {
    const order = [...columnOrder.value]
    const i = order.indexOf(key)
    if (i < order.length - 1) {
      ;[order[i], order[i + 1]] = [order[i + 1], order[i]]
      columnOrder.value = order
    }
  }

  function reset() {
    hiddenKeys.value = new Set(cols().filter((c) => !c.defaultVisible).map((c) => c.key))
    columnOrder.value = cols().map((c) => c.key)
  }

  function showAll() {
    hiddenKeys.value = new Set()
  }

  function hideAll() {
    hiddenKeys.value = new Set(cols().map((c) => c.key))
  }

    const sortedVisibleColumns = () => {
    const order = columnOrder.value
    const hidden = hiddenKeys.value
    const all = cols()
    return order.filter((k) => !hidden.has(k)).map((k) => all.find((c) => c.key === k)).filter(Boolean)
  }

    const sortedHiddenColumns = () => {
    const order = columnOrder.value
    const hidden = hiddenKeys.value
    const all = cols()
    return order.filter((k) => hidden.has(k)).map((k) => all.find((c) => c.key === k)).filter(Boolean)
  }

  return {
    hiddenKeys,
    columnOrder,
    isVisible,
    toggle,
    moveUp,
    moveDown,
    reset,
    showAll,
    hideAll,
    sortedVisibleColumns,
    sortedHiddenColumns,
  }
}

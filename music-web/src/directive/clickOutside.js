/*
 * v-click-outside — 点击元素外部时触发回调
 *
 * 使用方式：
 *   <div v-click-outside="() => visible = false">...</div>
 *
 * 适用场景：
 *   右键菜单关闭、下拉面板关闭、弹窗外部点击关闭
 */
export default {
  mounted(el, binding) {
    el.__clickOutside = (event) => {
      // 忽略右键（由 contextmenu 事件单独处理）
      if (event.button === 2) return
      // 点击目标在元素内部时不触发
      if (el.contains(event.target)) return
      binding.value(event)
    }
    // 使用 capture 阶段确保先于其他事件处理
    document.addEventListener('click', el.__clickOutside, true)
  },
  unmounted(el) {
    document.removeEventListener('click', el.__clickOutside, true)
    delete el.__clickOutside
  },
}



export default {
  mounted(el, binding) {
    el.__clickOutside = (event) => {
            if (event.button === 2) return
            if (el.contains(event.target)) return
      binding.value(event)
    }
        document.addEventListener('click', el.__clickOutside, true)
  },
  unmounted(el) {
    document.removeEventListener('click', el.__clickOutside, true)
    delete el.__clickOutside
  },
}

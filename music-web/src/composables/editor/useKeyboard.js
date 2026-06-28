/*
 * useKeyboard.js — 全局键盘快捷键管理
 *
 * 快捷键清单：
 *   Space      播放/暂停（焦点不在输入框时）
 *   ArrowUp    切换到上一个文件并选中
 *   ArrowDown  切换到下一个文件并选中
 *   E          打开/关闭编辑抽屉
 *   Tab        在编辑抽屉中跳转到下一个字段（浏览器默认行为）
 *   Ctrl+S     保存当前编辑中的元数据
 *   Ctrl+Z     撤销上一次编辑
 *   Ctrl+Y     重做被撤销的编辑
 *   Escape     关闭抽屉 / 清空文件选中
 *
 * 使用方式：在页面组件中调用 useKeyboard() 即可注册所有快捷键
 *   快捷键在输入框/文本域中自动失效（避免干扰文字输入）
 */
import { onMounted, onUnmounted } from 'vue'
import i18n from '@/i18n/index.js'
import { useAppStore } from '@/store/editor/app.js'
import { useFileStore } from '@/store/editor/file.js'
import { useEditStore } from '@/store/editor/edit.js'
import { useEditorPlayerStore } from '@/store/editor/player.js'
import { saveMetadata } from '@/api/editor/music.js'
import { toMusicMetadata } from '@/utils/musicMeta.js'

export function useKeyboard() {
  const app = useAppStore()
  const fileStore = useFileStore()
  const editStore = useEditStore()
  const player = useEditorPlayerStore()

  function handler(e) {
    // 输入框内不拦截（保留默认输入行为）
    const tag = e.target.tagName
    const isInput = tag === 'INPUT' || tag === 'TEXTAREA' || e.target.isContentEditable

    // ---- 全局快捷键（任意焦点生效） ----
    if (e.key === 'Escape') {
      if (app.drawerVisible) {
        app.closeDrawer()
        return
      }
      fileStore.clearSelection()
      return
    }

    // Ctrl+S：保存
    if (e.ctrlKey && e.key === 's') {
      e.preventDefault()
      if (editStore.isDirty && editStore.editingFile) {
        const artists = editStore.currentMeta.artists
        if (artists && artists.length > 0) {
          artists[editStore.currentMeta.activeArtistIndex] = { ...editStore.currentMeta.artist }
        }
        const meta = toMusicMetadata(editStore.currentMeta)
        saveMetadata(editStore.editingFile.path, meta)
        editStore.clearDraft()
        editStore.resetEdit()
        window.$message?.success(i18n.global.t('common.saveSuccess'))
      }
      return
    }

    // Ctrl+Z：撤销
    if (e.ctrlKey && !e.shiftKey && e.key === 'z') {
      e.preventDefault()
      if (!isInput) editStore.undo()
      return
    }

    // Ctrl+Y 或 Ctrl+Shift+Z：重做
    if ((e.ctrlKey && e.key === 'y') || (e.ctrlKey && e.shiftKey && e.key === 'z')) {
      e.preventDefault()
      if (!isInput) editStore.redo()
      return
    }

    // ---- 输入框内不触发的快捷键 ----
    if (isInput) return

    // Space：播放/暂停
    if (e.key === ' ') {
      e.preventDefault()
      player.togglePlay()
      return
    }

    // ArrowUp：上一个文件
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      const files = fileStore.filteredFiles
      if (files.length === 0) return
      const current = fileStore.selectedFiles[0]
      const idx = current ? files.findIndex((f) => f.id === current.id) : -1
      const nextIdx = idx > 0 ? idx - 1 : files.length - 1
      fileStore.clearSelection()
      fileStore.toggleSelect(files[nextIdx].id)
      return
    }

    // ArrowDown：下一个文件
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      const files = fileStore.filteredFiles
      if (files.length === 0) return
      const current = fileStore.selectedFiles[0]
      const idx = current ? files.findIndex((f) => f.id === current.id) : -1
      const nextIdx = idx < files.length - 1 ? idx + 1 : 0
      fileStore.clearSelection()
      fileStore.toggleSelect(files[nextIdx].id)
      return
    }

    // Ctrl+A：全选
    if (e.ctrlKey && e.key === 'a') {
      e.preventDefault()
      fileStore.selectAll()
      return
    }
  }

  onMounted(() => window.addEventListener('keydown', handler))
  onUnmounted(() => window.removeEventListener('keydown', handler))
}

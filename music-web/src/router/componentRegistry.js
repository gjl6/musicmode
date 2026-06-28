/**
 * 前端组件注册表 — component_key → Vue 懒加载组件。
 *
 * 后端 /api/auth/menus 返回的每个菜单节点携带 component 字段，
 * 前端通过此注册表查找对应的 Vue 组件并动态注册路由。
 *
 * 新增页面时：在此文件中添加一个 entry，在 DB auth_menu 表中添加一行。
 */
export const componentRegistry = {
  // ── Player layouts ──
  'PlayerLayout': () => import('@/components/playback/layout/PlayerLayout.vue'),
  'AdminLayout': () => import('@/views/auth/admin/AdminLayout.vue'),

  // ── Player views ──
  'PlayerHome': () => import('@/views/playback/PlayerHome.vue'),
  'PlayerSettings': () => import('@/views/playback/PlayerSettings.vue'),
  'SearchResults': () => import('@/views/playback/SearchResults.vue'),
  'ArtistList': () => import('@/views/playback/ArtistList.vue'),
  'ArtistDetail': () => import('@/views/playback/ArtistDetail.vue'),
  'AlbumList': () => import('@/views/playback/AlbumList.vue'),
  'AlbumDetail': () => import('@/views/playback/AlbumDetail.vue'),
  'GenreList': () => import('@/views/playback/GenreList.vue'),
  'GenreDetail': () => import('@/views/playback/GenreDetail.vue'),
  'SongList': () => import('@/views/playback/SongList.vue'),
  'SongDetail': () => import('@/views/playback/SongDetail.vue'),
  'PlaylistList': () => import('@/views/playback/PlaylistList.vue'),
  'PlaylistDetail': () => import('@/views/playback/PlaylistDetail.vue'),
  'PlaylistEdit': () => import('@/views/playback/PlaylistEdit.vue'),
  'FullscreenPlayer': () => import('@/views/playback/FullscreenPlayer.vue'),

  // ── Editor views ──
  'Workbench': () => import('@/views/editor/Workbench.vue'),
  'PipelineList': () => import('@/views/editor/PipelineList.vue'),
  'PipelineDetail': () => import('@/views/editor/PipelineDetail.vue'),
  'ConfigManager': () => import('@/views/editor/ConfigManager.vue'),
  'CustomProviderEditor': () => import('@/views/editor/CustomProviderEditor.vue'),
  'ArtistManager': () => import('@/views/editor/ArtistManager.vue'),
  'AlbumManager': () => import('@/views/editor/AlbumManager.vue'),

  // ── Admin views ──
  'UserManagement': () => import('@/views/auth/admin/UserManagement.vue'),
  'RoleManagement': () => import('@/views/auth/admin/RoleManagement.vue'),
  'PermissionList': () => import('@/views/auth/admin/PermissionList.vue'),
}

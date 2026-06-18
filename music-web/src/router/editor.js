

export default [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/editor/Home.vue'),
  },
  {
    path: '/workbench',
    name: 'Workbench',
    component: () => import('@/views/editor/Workbench.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/pipelines',
    name: 'PipelineList',
    component: () => import('@/views/editor/PipelineList.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/pipeline/:id',
    name: 'PipelineDetail',
    component: () => import('@/views/editor/PipelineDetail.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/config',
    name: 'ConfigManager',
    component: () => import('@/views/editor/ConfigManager.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/custom-providers/:id',
    name: 'CustomProviderEditor',
    component: () => import('@/views/editor/CustomProviderEditor.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/artist-manager',
    name: 'ArtistManager',
    component: () => import('@/views/editor/ArtistManager.vue'),
    meta: { requiresAuth: true },
  },
]

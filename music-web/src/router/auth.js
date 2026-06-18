

export default [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
  },
  {
    path: '/forbidden',
    name: 'Forbidden',
    component: () => import('@/views/auth/Forbidden.vue'),
  },
    {
    path: '/admin',
    component: () => import('@/views/auth/admin/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: '', redirect: { name: 'AdminUsers' } },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/auth/admin/UserManagement.vue'),
        meta: { requiresAuth: true, requiresAdmin: true },
      },
      {
        path: 'roles',
        name: 'AdminRoles',
        component: () => import('@/views/auth/admin/RoleManagement.vue'),
        meta: { requiresAuth: true, requiresAdmin: true },
      },
      {
        path: 'permissions',
        name: 'AdminPermissions',
        component: () => import('@/views/auth/admin/PermissionList.vue'),
        meta: { requiresAuth: true, requiresAdmin: true },
      },
    ],
  },
]

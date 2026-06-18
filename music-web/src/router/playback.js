

export default [
    {
    path: '/player',
    component: () => import('@/components/playback/layout/PlayerLayout.vue'),
    children: [
      {
        path: '',
        name: 'PlayerHome',
        component: () => import('@/views/playback/PlayerHome.vue'),
      },
            {
        path: 'artists',
        name: 'ArtistList',
        component: () => import('@/views/playback/ArtistList.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'artists/:id',
        name: 'ArtistDetail',
        component: () => import('@/views/playback/ArtistDetail.vue'),
        meta: { requiresAuth: true },
      },
            {
        path: 'albums',
        name: 'AlbumList',
        component: () => import('@/views/playback/AlbumList.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'albums/:id',
        name: 'AlbumDetail',
        component: () => import('@/views/playback/AlbumDetail.vue'),
        meta: { requiresAuth: true },
      },
            {
        path: 'genres',
        name: 'GenreList',
        component: () => import('@/views/playback/GenreList.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'genres/:name',
        name: 'GenreDetail',
        component: () => import('@/views/playback/GenreDetail.vue'),
        meta: { requiresAuth: true },
      },
            {
        path: 'songs',
        name: 'SongList',
        component: () => import('@/views/playback/SongList.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'songs/:id',
        name: 'SongDetail',
        component: () => import('@/views/playback/SongDetail.vue'),
        meta: { requiresAuth: true },
      },
            {
        path: 'playlists',
        name: 'PlaylistList',
        component: () => import('@/views/playback/PlaylistList.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'playlists/:id',
        name: 'PlaylistDetail',
        component: () => import('@/views/playback/PlaylistDetail.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'playlists/:id/edit',
        name: 'PlaylistEdit',
        component: () => import('@/views/playback/PlaylistEdit.vue'),
        meta: { requiresAuth: true },
      },
    ],
  },

    {
    path: '/player/fullscreen',
    name: 'FullscreenPlayer',
    component: () => import('@/views/playback/FullscreenPlayer.vue'),
    meta: { requiresAuth: true },
  },

]

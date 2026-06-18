package com.gjl.music.playback.service;

import java.util.List;


public interface SearchService {


    List<Long> searchSongs(String query, int offset, int limit);


    List<Long> searchAlbums(String query, int offset, int limit);


    List<Long> searchArtists(String query, int offset, int limit);
}

package com.gjl.music.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 单文件音乐元数据聚合体 —— 包含歌曲、专辑、艺术家、歌词、风格的集合 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class MusicMetadata {
    @JsonProperty
    private List<Song> songs = new ArrayList<>();
    @JsonProperty
    private List<Album> albums = new ArrayList<>();
    @JsonProperty
    private List<Artist> artists = new ArrayList<>();
    @JsonProperty
    private List<Lyric> lyrics = new ArrayList<>();
    @JsonProperty
    private List<Style> styles = new ArrayList<>();

    public List<Song> getImmutableSongs() {
        return Collections.unmodifiableList(songs);
    }

    public List<Artist> getImmutableArtists() {
        return Collections.unmodifiableList(artists);
    }

    public List<Album> getImmutableAlbums() {
        return Collections.unmodifiableList(albums);
    }

    public List<Lyric> getImmutableLyrics() {
        return Collections.unmodifiableList(lyrics);
    }

    public List<Style> getImmutableStyles() {
        return Collections.unmodifiableList(styles);
    }

    public MusicMetadata addSong(Song song) {
        songs.add(song);
        return this;
    }

    public MusicMetadata addAlbum(Album album) {
        albums.add(album);
        return this;
    }

    public MusicMetadata addArtist(Artist artist) {
        artists.add(artist);
        return this;
    }

    public MusicMetadata addLyric(Lyric lyric) {
        lyrics.add(lyric);
        return this;
    }

    public MusicMetadata addStyle(Style style) {
        styles.add(style);
        return this;
    }
}

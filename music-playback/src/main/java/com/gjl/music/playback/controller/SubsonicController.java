package com.gjl.music.playback.controller;

import com.gjl.music.playback.infra.subsonic.SubsonicRequestParams;
import com.gjl.music.playback.infra.subsonic.SubsonicResponseBuilder;
import com.gjl.music.playback.service.SubsonicService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
public class SubsonicController {

    private final SubsonicService subsonicService;
    private final SubsonicResponseBuilder responseBuilder;

    public SubsonicController(SubsonicService subsonicService,
                              SubsonicResponseBuilder responseBuilder) {
        this.subsonicService = subsonicService;
        this.responseBuilder = responseBuilder;
    }

    @GetMapping({"/rest/{action}", "/rest/{action}.view"})
    public void handleGet(@PathVariable String action,
                          HttpServletRequest request,
                          HttpServletResponse response) throws IOException {
        handle(action, request, response);
    }

    @PostMapping({"/rest/{action}", "/rest/{action}.view"})
    public void handlePost(@PathVariable String action,
                           HttpServletRequest request,
                           HttpServletResponse response) throws IOException {
        handle(action, request, response);
    }

    private void handle(String action, HttpServletRequest request,
                        HttpServletResponse response) throws IOException {
        SubsonicRequestParams p = new SubsonicRequestParams(request);
        String fmt = p.format();
        String cb = p.callback();

        try {
            Object result = dispatch(action, p, request, response);
            if (result == null) {
                writeResponse(response, responseBuilder.buildError(70, "数据未找到"), fmt, cb);
                return;
            }
            if (result instanceof ResponseSent) return;
            @SuppressWarnings("unchecked")
            Map<String, Object> mapResult = (Map<String, Object>) result;
            writeResponse(response, responseBuilder.buildOk(mapResult), fmt, cb);
        } catch (IllegalArgumentException e) {
            writeResponse(response, responseBuilder.buildError(10, e.getMessage()), fmt, cb);
        } catch (Exception e) {
            log.error("Subsonic API 异常: action={}", action, e);
            writeResponse(response, responseBuilder.buildError(0, e.getMessage()), fmt, cb);
        } finally {
            subsonicService.cleanupRequest();
        }
    }

    private Object dispatch(String action, SubsonicRequestParams p,
                            HttpServletRequest request,
                            HttpServletResponse response) throws IOException {
        return switch (action) {
            // System
            case "ping" -> subsonicService.ping();
            case "getLicense" -> subsonicService.getLicense();
            case "getNowPlaying" -> subsonicService.getNowPlaying();
            case "getOpenSubsonicExtensions" -> subsonicService.getOpenSubsonicExtensions();

            // Browsing
            case "getMusicFolders" -> subsonicService.getMusicFolders();
            case "getIndexes" -> subsonicService.getIndexes(p);
            case "getArtists" -> subsonicService.getArtists(p);
            case "getMusicDirectory" -> subsonicService.getMusicDirectory(p);
            case "getArtist" -> subsonicService.getArtist(p);
            case "getArtistInfo", "getArtistInfo2" -> subsonicService.getArtistInfo2(p);
            case "getAlbumInfo", "getAlbumInfo2" -> subsonicService.getAlbumInfo2(p);
            case "getAlbum" -> subsonicService.getAlbum(p);
            case "getSong" -> subsonicService.getSong(p);
            case "getGenres" -> subsonicService.getGenres();

            // Album Lists
            case "getAlbumList", "getAlbumList2" -> subsonicService.getAlbumList(p);
            case "getRandomSongs" -> subsonicService.getRandomSongs(p);
            case "getSongsByGenre" -> subsonicService.getSongsByGenre(p);
            case "getSimilarSongs", "getSimilarSongs2" -> subsonicService.getSimilarSongs(p);
            case "getTopSongs" -> subsonicService.getTopSongs(p);
            case "getSongs" -> subsonicService.getSongs(p);
            case "getSongLetters" -> subsonicService.getSongLetters();

            // Searching
            case "search" -> subsonicService.searchLegacy(p);
            case "search2", "search3" -> subsonicService.search(p);

            // Playlists
            case "getPlaylists" -> subsonicService.getPlaylists(p);
            case "getPlaylist" -> subsonicService.getPlaylist(p);
            case "createPlaylist" -> subsonicService.createPlaylist(p, request);
            case "deletePlaylist" -> subsonicService.deletePlaylist(p);
            case "updatePlaylist" -> subsonicService.updatePlaylist(p, request);

            // Media Retrieval
            case "getCoverArt" -> {
                subsonicService.getCoverArt(p, response);
                yield new ResponseSent();
            }
            case "stream" -> {
                subsonicService.stream(p, request, response);
                yield new ResponseSent();
            }
            case "download" -> {
                subsonicService.download(p, request, response);
                yield new ResponseSent();
            }
            case "getAvatar" -> {
                subsonicService.getAvatar(p, response);
                yield new ResponseSent();
            }

            // User
            case "getUser" -> subsonicService.getUser(p);
            case "getUsers" -> subsonicService.getUsers(p);
            case "changePassword" -> subsonicService.changePassword(p);

            // Play Queue
            case "getPlayQueue" -> subsonicService.getPlayQueue(p);
            case "savePlayQueue" -> subsonicService.savePlayQueue(p, request);

            // Library Scanning
            case "getScanStatus" -> subsonicService.getScanStatus(p);
            case "startScan" -> subsonicService.startScan(p);

            // Lyrics
            case "getLyrics" -> subsonicService.getLyrics(p);
            case "getLyricsBySongId" -> subsonicService.getLyricsBySongId(p);

            // User Data
            case "scrobble" -> subsonicService.scrobble(p);
            case "star" -> subsonicService.star(p);
            case "unstar" -> subsonicService.unstar(p);
            case "getStarred" -> subsonicService.getStarred(p);
            case "getStarred2" -> subsonicService.getStarred2(p);
            case "setRating" -> subsonicService.setRating(p);
            case "reportPlayback" -> subsonicService.reportPlayback(p);

            default -> throw new IllegalArgumentException("不支持的端点: " + action);
        };
    }

    private void writeResponse(HttpServletResponse response, Map<String, Object> data,
                               String fmt, String cb) throws IOException {
        String serialized = responseBuilder.serialize(data, fmt, cb);
        String contentType = "json".equals(fmt) || "jsonp".equals(fmt)
                ? "application/json;charset=UTF-8"
                : "application/xml;charset=UTF-8";
        response.setContentType(contentType);
        response.getWriter().write(serialized);
    }

    private static class ResponseSent {}
}

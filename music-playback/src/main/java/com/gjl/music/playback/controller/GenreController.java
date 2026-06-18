package com.gjl.music.playback.controller;

import com.gjl.music.playback.service.GenreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/genres")
public class GenreController {

    private final GenreService genreService;

    public GenreController(GenreService genreService) {
        this.genreService = genreService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getGenres(
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(required = false) String letter,
            @RequestParam(defaultValue = "60") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(genreService.getGenres(sort, letter, limit, offset));
    }

    @GetMapping("/letters")
    public ResponseEntity<Map<String, Object>> getGenreLetters() {
        return ResponseEntity.ok(genreService.getGenreLetters());
    }

    @GetMapping("/{name}")
    public ResponseEntity<Map<String, Object>> getGenreSongs(
            @PathVariable String name,
            @RequestParam(required = false) String letter,
            @RequestParam(defaultValue = "alphabetical") String sort,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            Principal principal) {
        Long userId = genreService.resolveUserId(principal != null ? principal.getName() : null);
        return ResponseEntity.ok(genreService.getGenreSongs(name, letter, sort, limit, offset, userId));
    }
}

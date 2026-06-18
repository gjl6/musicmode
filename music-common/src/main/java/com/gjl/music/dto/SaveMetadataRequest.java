package com.gjl.music.dto;

import java.util.List;
import java.util.Map;

public record SaveMetadataRequest(String path, Map<String, Object> metadata) {}

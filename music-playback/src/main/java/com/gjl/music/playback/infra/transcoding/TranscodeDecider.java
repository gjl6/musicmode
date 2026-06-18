package com.gjl.music.playback.infra.transcoding;

import com.gjl.music.model.Song;
import com.gjl.music.playback.model.TranscodeDecision;


public interface TranscodeDecider {


    TranscodeDecision decide(Song song, String reqFormat, int maxBitRate);
}

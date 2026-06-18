package com.gjl.music.parser;

import com.gjl.music.exception.MetadataParseException;
import com.gjl.music.model.MusicMetadata;

import java.io.File;


public interface ParserFactory {


    MusicMetadata parse(File file) throws MetadataParseException;
}

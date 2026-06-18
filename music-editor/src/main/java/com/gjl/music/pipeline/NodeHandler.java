package com.gjl.music.pipeline;


@FunctionalInterface
public interface NodeHandler {


    NodeResult execute(NodeContext ctx) throws Exception;
}

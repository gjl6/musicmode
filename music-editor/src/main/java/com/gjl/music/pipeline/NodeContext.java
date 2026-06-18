package com.gjl.music.pipeline;

import java.util.Map;
import java.util.concurrent.Executor;


public interface NodeContext {


    String getPipelineId();


    @SuppressWarnings("unchecked")
    default <T> T getSlot(String key) {
        return (T) slots().get(key);
    }

    default void setSlot(String key, Object value) {
        slots().put(key, value);
    }


    Map<String, Object> slots();


    Map<String, Object> invoke(String moduleName, Object input) throws Exception;


    boolean isCancelled();


    boolean isPausing();


    void checkPause() throws CancelledException;


    Executor getWorkerExecutor();


    default void reportItemComplete(String itemKey, boolean success, String errorMessage) {
            }


    final class CancelledException extends RuntimeException {
        public CancelledException() {
            super("Pipeline cancelled");
        }
    }
}

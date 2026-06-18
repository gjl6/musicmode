package com.gjl.music.watch;

import com.gjl.music.model.WatchRule;

import java.util.Collection;


public final class WatchRuleMatcher {

    private WatchRuleMatcher() {}


    public static WatchRule findMatching(Collection<WatchRule> rules, String relativePath) {
        if (rules == null || rules.isEmpty() || relativePath == null) return null;

        WatchRule best = null;
        int bestLen = -2;

        for (WatchRule r : rules) {
            String watchPath = r.getWatchPath();
                        String prefix = "/".equals(watchPath) ? "" : watchPath;
            if (!prefix.isEmpty() && !prefix.endsWith("/")) {
                prefix += "/";
            }

            if (relativePath.startsWith(prefix)) {
                int matchLen = prefix.isEmpty() ? -1 : prefix.length();

                if (matchLen > bestLen || (matchLen == bestLen && best != null
                        && r.getPriority() > best.getPriority())) {
                    best = r;
                    bestLen = matchLen;
                }
            }
        }

        return best;
    }
}

package com.cacheeviction.distributed.global.util;

import java.util.ArrayList;
import java.util.List;

public class KMP {

    public static List<String> search(String pattern, String text) {
        String[] lines = text.split("\n");
        List<String> matches = new ArrayList<>();

        for (String line : lines) {
            if (containsPattern(line, pattern)) {
                matches.add(line);
            }
        }

        return matches;
    }

    private static boolean containsPattern(String line, String pattern) {
        int m = pattern.length();
        int n = line.length();
        int j = 0;
        int[] prefixTable = buildPrefixTable(pattern);

        int i = 0;
        while (i < n) {
            if (pattern.charAt(j) == line.charAt(i)) {
                j++;
                i++;
            }

            if (j == m) {
                return true;
            } else if (i < n && pattern.charAt(j) != line.charAt(i)) {
                if (j != 0) {
                    j = prefixTable[j - 1];
                } else {
                    i++;
                }
            }
        }

        return false;
    }

    private static int[] buildPrefixTable(String pattern) {
        int[] pt = new int[pattern.length()];

        int len = 0;
        pt[0] = 0;

        int i = 1;
        while (i < pattern.length()) {
            if (pattern.charAt(i) == pattern.charAt(len)) {
                len++;
                pt[i] = len;
                i++;
            } else {
                if (len != 0) {
                    len = pt[len - 1];
                } else {
                    pt[i] = len;
                    i++;
                }
            }
        }

        return pt;
    }
}

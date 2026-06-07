package com.att.tdp.issueflow.comment;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts {@code @username} tokens from comment content. Tokens are returned
 * lowercased and de-duplicated so callers can match users case-insensitively;
 * order of first appearance is preserved for deterministic results.
 */
@Component
public class MentionParser {

    /** A token is '@' followed by username-legal characters (letters, digits, '_', '.', '-'). */
    private static final Pattern MENTION = Pattern.compile("@([A-Za-z0-9_.-]+)");

    public Set<String> parse(String content) {
        Set<String> usernames = new LinkedHashSet<>();
        if (content == null || content.isBlank()) {
            return usernames;
        }
        Matcher matcher = MENTION.matcher(content);
        while (matcher.find()) {
            usernames.add(matcher.group(1).toLowerCase());
        }
        return usernames;
    }
}

package com.att.tdp.issueflow.comment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests for {@code @username} token extraction. */
class MentionParserTest {

    private final MentionParser parser = new MentionParser();

    @Test
    void extractsSingleMention() {
        assertThat(parser.parse("Hey @jdoe please review")).containsExactly("jdoe");
    }

    @Test
    void extractsMultipleMentionsInOrder() {
        assertThat(parser.parse("@alice and @bob, ping @carol"))
                .containsExactly("alice", "bob", "carol");
    }

    @Test
    void lowercasesTokensForCaseInsensitiveMatching() {
        assertThat(parser.parse("@JDoe @BOB")).containsExactly("jdoe", "bob");
    }

    @Test
    void deduplicatesRepeatedMentions() {
        assertThat(parser.parse("@jdoe @jdoe @JDOE")).containsExactly("jdoe");
    }

    @Test
    void stopsAtNonUsernamePunctuation() {
        // The token ends at the comma / whitespace, not consuming trailing punctuation.
        assertThat(parser.parse("ping @jdoe, thanks!")).containsExactly("jdoe");
    }

    @Test
    void returnsEmptyForNoMentions() {
        assertThat(parser.parse("no mentions here")).isEmpty();
    }

    @Test
    void returnsEmptyForNullOrBlank() {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("   ")).isEmpty();
    }
}

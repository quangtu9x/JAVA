package com.td.application.vbden;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VbdenParticipantCheckerTest {

    @Test
    void matchesUsernameInListOrDelimitedUserFields() {
        assertThat(VbdenParticipantChecker.isParticipant(Map.of("user_process", List.of("demo12", "demo13")), "demo13")).isTrue();
        assertThat(VbdenParticipantChecker.isParticipant(Map.of("user_to_read", "demo1;Demo13;"), "demo13")).isTrue();
        assertThat(VbdenParticipantChecker.isParticipant(Map.of("user_coordinate", "demo1, demo13"), "demo13")).isTrue();
        assertThat(VbdenParticipantChecker.isParticipant(Map.of("author", List.of("demo13")), "demo13")).isTrue();
    }

    @Test
    void ignoresOtherUsersObjFieldsAndPartialMatches() {
        assertThat(VbdenParticipantChecker.isParticipant(Map.of("user_process", "demo130;demo1"), "demo13")).isFalse();
        assertThat(VbdenParticipantChecker.isParticipant(
            Map.of("user_process_obj", "[{\"username\":\"demo13\"}]"), "demo13")).isFalse();
        assertThat(VbdenParticipantChecker.isParticipant(Map.of("title", "demo13"), "demo13")).isFalse();
        assertThat(VbdenParticipantChecker.isParticipant(null, "demo13")).isFalse();
    }
}

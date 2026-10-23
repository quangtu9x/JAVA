package com.td.application.duthao;

import com.td.application.sharedcore.UserDelegationTestSupport;
import com.td.domain.sharedcore.UserDelegation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DuThaoCoordinatorDelegationsTest {

    private static final String COORDINATOR_OBJ =
        "[{\"id\":\"1\",\"name\":\"Truong phong A\",\"username\":\"phophongb\",\"delegated_from_username\":\"truongphonga\"},"
            + "{\"id\":\"2\",\"name\":\"Chuyen vien C\",\"username\":\"chuyenvienc\"}]";

    private DuThaoCoordinatorDelegations withDelegation(String scopes) {
        var delegation = new UserDelegation(UUID.randomUUID(), "truongphonga", UUID.randomUUID(), "phophongb",
            "Pho phong B", "Phó Trưởng phòng", LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), scopes, "");
        return new DuThaoCoordinatorDelegations(UserDelegationTestSupport.resolverWith(new ArrayList<>(List.of(delegation))));
    }

    @Test
    void apply_verifiedDelegation_shouldAppendDelegatorToCoordinatedMovement() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("coordinator_obj", COORDINATOR_OBJ);
        fields.put("user_coordinated_movement", "cu");

        withDelegation("duthao").apply(fields);

        assertThat(fields).containsEntry("user_coordinated_movement", "cu;truongphonga");
    }

    @Test
    void apply_twice_shouldNotDuplicate() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("coordinator_obj", COORDINATOR_OBJ);
        DuThaoCoordinatorDelegations delegations = withDelegation("duthao");

        delegations.apply(fields);
        delegations.apply(fields);

        assertThat(fields).containsEntry("user_coordinated_movement", "truongphonga");
    }

    @Test
    void apply_delegationOutsideDuThaoScope_shouldIgnore() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("coordinator_obj", COORDINATOR_OBJ);

        withDelegation("vbden").applyToStringFields(fields);

        assertThat(fields).doesNotContainKey("user_coordinated_movement");
    }

    @Test
    void apply_malformedCoordinatorObj_shouldIgnoreWithoutError() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("coordinator_obj", "{not json");

        withDelegation("duthao").apply(fields);

        assertThat(fields).doesNotContainKey("user_coordinated_movement");
    }
}

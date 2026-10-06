package com.td.application.privatecatalog;

import com.td.application.sharedcore.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// 06/10/2026: thành viên "nhóm người nhận" đã bị Khoá/Xoá không được chọn ở màn chọn người.
@ExtendWith(MockitoExtension.class)
class ActiveGroupMembersFilterTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Test
    void filter_shouldDropInactiveMembers_keepNonUserEntries_andPreserveInputShape() {
        when(appUserRepository.findActiveUsernamesIn(any())).thenReturn(Set.of("active01"));
        List<Object> input = new ArrayList<>(Arrays.asList(
            List.of(Map.of("username", "active01", "name", "A"), Map.of("username", "locked01", "name", "B")),
            "[{\"username\":\"locked01\"},{\"id\":\"x\"}]",
            "",
            null));

        List<Object> result = new ActiveGroupMembersFilter(appUserRepository).filter(input);

        assertThat(result.get(0)).isEqualTo(List.of(Map.of("username", "active01", "name", "A")));
        assertThat(result.get(1)).isEqualTo("[{\"id\":\"x\"}]");
        assertThat(result.get(2)).isEqualTo("");
        assertThat(result.get(3)).isNull();
    }
}

package com.td.application.documents;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.GetUserOrganizationProfileUseCase;
import com.td.application.sharedcore.UserOrganizationProfileDto;
import com.td.domain.sharedcore.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 16/09/2026 (yêu cầu người dùng "làm đầy đủ luôn" cơ chế `author`) — xem javadoc
 * {@link AuthorAclFieldsService} về đối chiếu Domino gốc (`TDDocument.java#CreateOrUpdateDocument()`
 * + `services.duthao.AddRolesToDocument` + hằng số `TDKeySettings.SET_AUTHOR_FIELDS`).
 */
@ExtendWith(MockitoExtension.class)
class AuthorAclFieldsServiceTest {

    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;

    private AuthorAclFieldsService newService() {
        return new AuthorAclFieldsService(currentUserProvider, appUserRepository, getUserOrganizationProfileUseCase);
    }

    @Test
    void applyOnCreate_withNullFields_shouldDoNothing() {
        newService().applyOnCreate("vbdi", null);
        // Khong throw - khong co gi de assert them (khong co map de kiem tra).
    }

    @Test
    void applyOnCreate_withExcludedDocumentType_shouldNotSetAuthor() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("user_creator", "nva");

        newService().applyOnCreate("vbcddh", fields);

        assertThat(fields).doesNotContainKey("author");
        verify(currentUserProvider, never()).currentUsername();
    }

    // Domino: creator (user_creator) LUON duoc gom, du khong nam trong SET_AUTHOR_FIELDS ten khac.
    @Test
    void applyOnCreate_shouldAlwaysIncludeUserCreator() {
        noCurrentUser();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("user_creator", "nva");

        newService().applyOnCreate("vbden", fields);

        assertThat(fields.get("author")).isEqualTo("nva");
    }

    // "user_fields" dong: MOI field ten bat dau "user_" co mat trong request duoc gom vao.
    @Test
    void applyOnCreate_shouldIncludeValuesOfUserPrefixedFields() {
        noCurrentUser();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("user_creator", "nva");
        fields.put("user_process", "chuyenvien01;chuyenvien02");
        fields.put("user_coordinate", "phoihop01");
        fields.put("subject", "Khong lien quan");

        newService().applyOnCreate("vbden", fields);

        String author = fields.get("author");
        assertThat(author.split(";")).containsExactlyInAnyOrder("nva", "chuyenvien01", "chuyenvien02", "phoihop01");
    }

    // Field ket thuc "hide" cung duoc gom (dung Domino - getFieldName.endsWith("hide")).
    @Test
    void applyOnCreate_shouldIncludeValuesOfFieldsEndingWithHide() {
        noCurrentUser();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("somethinghide", "userxyz");

        newService().applyOnCreate("vbden", fields);

        assertThat(fields.get("author")).isEqualTo("userxyz");
    }

    // TDKeySettings.SET_AUTHOR_FIELDS - hang so co dinh, tron username lan identifier to chuc.
    @Test
    void applyOnCreate_shouldIncludeSetAuthorFieldsValues() {
        noCurrentUser();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("unit_publish_identifier", "H05.12");
        fields.put("unit_creator_identifier", "H05.01");
        fields.put("unit_in_charge_identifier", "H05.07");
        fields.put("identifier", "H05");
        fields.put("submission_dossier_identity", "SD-01");
        fields.put("coordinator_hide_rtext", "phoihop01;phoihop02");

        newService().applyOnCreate("vbdi", fields);

        String author = fields.get("author");
        assertThat(author.split(";")).containsExactlyInAnyOrder(
            "H05.12", "H05.01", "H05.07", "H05", "SD-01", "phoihop01", "phoihop02");
    }

    // Identifier cua NGUOI THUC HIEN luu (chuoi to tien LA->GOC) cung duoc gom.
    @Test
    void applyOnCreate_shouldIncludeCurrentUserIdentifiers() {
        AppUser user = newUser("nv1");
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nv1"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nv1")).thenReturn(Optional.of(user));
        UserOrganizationProfileDto profile = new UserOrganizationProfileDto();
        profile.setIdentifiers("H05.12;H05;G10;");
        when(getUserOrganizationProfileUseCase.buildProfileForUser(user)).thenReturn(profile);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("user_creator", "nv1");

        newService().applyOnCreate("vbden", fields);

        String author = fields.get("author");
        assertThat(author.split(";")).contains("H05.12", "H05", "G10");
    }

    // Dedup: cung 1 gia tri xuat hien o nhieu nguon (vd user_creator VA SET_AUTHOR_FIELDS) chi giu 1 lan.
    @Test
    void applyOnCreate_shouldDeduplicateAcrossSources() {
        noCurrentUser();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("user_creator", "nva");
        fields.put("user_draft", "nva");

        newService().applyOnCreate("vbdi", fields);

        assertThat(fields.get("author")).isEqualTo("nva");
    }

    // Bugfix 16/09/2026 (3): bug that nguoi dung phat hien - FE "Them moi" chuan KHONG gui
    // "user_creator" (chi gui "creator" - ten hien thi) nen author mat nguoi tao. Domino that LUON
    // ghi de user_creator = username nguoi dang thao tac cho document MOI, khong phu thuoc client.
    @Test
    void applyOnCreate_withoutClientSuppliedUserCreator_shouldStillIncludeCurrentUsername() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("subject", "Van ban moi");

        newService().applyOnCreate("vbdi", fields);

        assertThat(fields.get("user_creator")).isEqualTo("vanthu01");
        assertThat(fields.get("author")).isEqualTo("vanthu01");
    }

    // SERVER-AUTHORITATIVE (dung tien le TaoDuThaoUseCase): CREATE luon ghi de, khong tin gia tri
    // client gui (chan mao danh nguoi tao).
    @Test
    void applyOnCreate_withClientSuppliedDifferentUserCreator_shouldOverwriteWithCurrentUsername() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("user_creator", "mao_danh");

        newService().applyOnCreate("vbdi", fields);

        assertThat(fields.get("user_creator")).isEqualTo("vanthu01");
        assertThat(fields.get("author")).isEqualTo("vanthu01");
    }

    @Test
    void applyOnUpdate_shouldBackfillBlankUserCreatorFromCurrentUser() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("user_creator", "");
        fields.put("subject", "Van ban cu");

        newService().applyOnUpdate("vbdi", fields, Set.of());

        assertThat(fields.get("user_creator")).isEqualTo("vanthu01");
        assertThat(fields.get("author")).isEqualTo("vanthu01");
    }

    // Khac applyOnCreate: hieu chinh KHONG duoc doi danh tinh nguoi tao goc da co san.
    @Test
    void applyOnUpdate_shouldNotOverwriteExistingUserCreator() {
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguoisua02"));
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("user_creator", "nguoitaogoc01");

        newService().applyOnUpdate("vbdi", fields, Set.of());

        assertThat(fields.get("user_creator")).isEqualTo("nguoitaogoc01");
        assertThat(fields.get("author")).isEqualTo("nguoitaogoc01");
    }

    @Test
    void applyOnCreate_withNoRelevantValues_shouldNotSetAuthor() {
        noCurrentUser();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("subject", "Khong lien quan");

        newService().applyOnCreate("vbden", fields);

        assertThat(fields).doesNotContainKey("author");
    }

    // applyOnUpdate: khac applyOnCreate o cho gia tri field co the la List<String> (da qua
    // parseDynamicFields) va "touchedFieldNames" tach rieng khoi ban than field map.
    @Test
    void applyOnUpdate_shouldReadListValuedFieldsAndDedupeAgainstExistingAuthor() {
        noCurrentUser();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("author", List.of("nva", "cu01"));
        fields.put("user_process", List.of("chuyenvien01", "nva"));
        fields.put("subject", "Khong lien quan");

        newService().applyOnUpdate("vbden", fields, Set.of("user_process"));

        String author = (String) fields.get("author");
        assertThat(author.split(";")).containsExactlyInAnyOrder("nva", "cu01", "chuyenvien01");
    }

    // touchedFieldNames chi anh huong nguon (1) - nguon (3) SET_AUTHOR_FIELDS van doc GIA TRI HIEN
    // TAI tren document du field do KHONG nam trong touchedFieldNames (dung Domino: doc lai sau khi
    // field khac da ghi, khong phan biet "vua doi" hay "co san tu truoc").
    @Test
    void applyOnUpdate_shouldReadSetAuthorFieldsRegardlessOfTouchedFieldNames() {
        noCurrentUser();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("unit_publish_identifier", "H05.12");
        fields.put("opinion", "Dong y");

        newService().applyOnUpdate("vbdi", fields, Set.of("opinion"));

        assertThat(((String) fields.get("author")).split(";")).contains("H05.12");
    }

    @Test
    void applyOnUpdate_withNullTouchedFieldNames_shouldSkipDynamicUserFieldsButKeepOtherSources() {
        noCurrentUser();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("user_process", "chuyenvien01");
        fields.put("user_creator", "nva");

        newService().applyOnUpdate("vbden", fields, null);

        String author = (String) fields.get("author");
        // user_creator LUON duoc gom (khong phu thuoc touchedFieldNames) - user_process (chi qua
        // nguon "user_fields dong") thi KHONG, vi touchedFieldNames=null.
        assertThat(author.split(";")).containsExactlyInAnyOrder("nva");
    }

    @Test
    void applyOnUpdate_withExcludedDocumentType_shouldNotSetAuthor() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("user_creator", "nva");

        newService().applyOnUpdate("vbcddh", fields, Set.of("user_creator"));

        assertThat(fields).doesNotContainKey("author");
    }

    private void noCurrentUser() {
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());
    }

    private AppUser newUser(String username) {
        return new AppUser("kc-" + username, username, "Nguyen Van A", null, null, null, true,
            null, 0, null, null, null, null, null, null, null, false, null);
    }
}

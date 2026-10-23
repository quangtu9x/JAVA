package com.td.application.duthao;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.duthao.DraftProcessDocument;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DuThaoSignSubmissionInspectorTest {

    /** Vết theo thứ tự MỚI NHẤT TRƯỚC — đúng hợp đồng findByParentId. */
    private final List<DraftProcessDocument> newestFirst = new ArrayList<>();

    private final DuThaoSignSubmissionInspector inspector =
        new DuThaoSignSubmissionInspector(stubRepository(newestFirst), new DynamicDocumentFieldsService());

    @Test
    void signerHasNotActedSinceSubmission() {
        newestFirst.add(record("chuyenkyduyet", "chuyenvien1"));
        newestFirst.add(record("gankemtep", "giamdoc"));   // trước lần trình -> không tính

        var result = inspector.inspect(UUID.randomUUID(), "giamdoc");

        assertThat(result.submitterUsername()).isEqualTo("chuyenvien1");
        assertThat(result.signerActed()).isFalse();
    }

    @Test
    void signerSignedOrCommentedAfterSubmission_countsAsActed() {
        newestFirst.add(record("gankemtep", "GiamDoc"));
        newestFirst.add(record("chuyenkyduyet", "chuyenvien1"));

        var result = inspector.inspect(UUID.randomUUID(), "giamdoc");

        assertThat(result.submitterUsername()).isEqualTo("chuyenvien1");
        assertThat(result.signerActed()).isTrue();
    }

    @Test
    void otherUsersRecordsAfterSubmission_doNotCount() {
        newestFirst.add(record("gankemtep", "chuyenvien1"));
        newestFirst.add(record("chuyenkyduyet", "chuyenvien1"));

        assertThat(inspector.inspect(UUID.randomUUID(), "giamdoc").signerActed()).isFalse();
    }

    /** Dùng chung cho test use case khác cần inspector không có vết nào. */
    // 19/10/2026: người kết thúc = vết ketthuc MỚI NHẤT (dự thảo kết thúc trước khi có user_ended).
    @Test
    void endedBy_shouldReturnActorOfLatestKetThucRecord() {
        newestFirst.add(record("ketthuc", "truongphong"));
        newestFirst.add(record("thuhoi", "truongphong"));
        newestFirst.add(record("ketthuc", "chuyenvien1"));

        assertThat(inspector.endedBy(UUID.randomUUID())).isEqualTo("truongphong");
    }

    @Test
    void endedBy_withoutKetThucRecord_shouldBeEmpty() {
        newestFirst.add(record("chuyenduyet", "chuyenvien1"));

        assertThat(inspector.endedBy(UUID.randomUUID())).isEmpty();
    }

    static DuThaoSignSubmissionInspector emptyInspector() {
        return new DuThaoSignSubmissionInspector(stubRepository(List.of()), new DynamicDocumentFieldsService());
    }

    private static DraftProcessDocument record(String action, String userCreator) {
        return new DraftProcessDocument("Vết", "draft_process", "1", null, null, null, null)
            .replaceDynamicFields("{\"action_type\":\"" + action + "\",\"user_creator\":\"" + userCreator + "\"}");
    }

    private static DraftProcessDocumentRepository stubRepository(List<DraftProcessDocument> records) {
        return new DraftProcessDocumentRepository() {
            @Override
            public <S extends DraftProcessDocument> S save(S entity) {
                return entity;
            }

            @Override
            public Optional<DraftProcessDocument> findById(UUID id) {
                return Optional.empty();
            }

            @Override
            public Page<DraftProcessDocument> findByParentId(String parentId, Pageable pageable) {
                return new PageImpl<>(records);
            }
        };
    }
}

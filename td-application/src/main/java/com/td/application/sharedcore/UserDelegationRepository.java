package com.td.application.sharedcore;

import com.td.domain.sharedcore.UserDelegation;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 09/10/2026 — bảng {@code user_delegations} (ủy quyền xử lý thay), xem {@link UserDelegation}. */
public interface UserDelegationRepository {

    <S extends UserDelegation> S save(S entity);

    Optional<UserDelegation> findByIdAndDeletedOnIsNull(UUID id);

    /** Mọi lần ủy quyền (kể cả đã huỷ/hết hạn) của 1 người — mới nhất trước. */
    List<UserDelegation> findByDelegatorUserId(UUID delegatorUserId);

    /** Ủy quyền đang hiệu lực ngày {@code day} của các người ủy quyền {@code usernames} (không phân biệt hoa thường). */
    List<UserDelegation> findActiveByDelegatorUsernamesOn(Collection<String> usernames, LocalDate day);

    /** Mọi ủy quyền đang hiệu lực ngày {@code day} (bảng nhỏ — dùng khi dựng cả cây tổ chức). */
    List<UserDelegation> findActiveOn(LocalDate day);
}

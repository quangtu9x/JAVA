package com.td.domain.workcalendars;

import com.td.domain.common.contracts.AuditableEntity;
import com.td.domain.common.contracts.IAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port module "Lịch công tác" (lichct) Domino → Java, 08/09/2026 — xem
 * {@code docs/analysis/09-phan-tich-port-lich-cong-tac-domino-sang-java.md} mục 7.2/7.3.
 *
 * <p>Bảng vật lý ĐỘC LẬP {@code work_calendars} (KHÔNG dùng chung {@code documents}) — CỐ Ý gần
 * giống {@link com.td.domain.duthao.DuThaoDocument}/{@code VbcddhDocument} (cùng kiến trúc BL-101:
 * Dynamic Data Engine, field nghiệp vụ biến thiên nằm trong 1 cột JSONB) nhưng KHÔNG kế thừa entity
 * nào khác — mỗi tuần/1 chủ thể (canhan/phong/donvi/coquan) là 1 dòng.
 *
 * <p><b>Field tĩnh</b>: {@code scope} (Domino {@code type}: canhan/phong/donvi/coquan — FE
 * {@code lanhdaotinh} được lưu như {@code coquan} trên CHÍNH tenant hiện tại, xem javadoc
 * {@link com.td.application.workcalendars.SaveWorkCalendarUseCase} mục "Quyết định bảo mật"),
 * {@code weekStart} (Domino {@code thu2}, thứ 2 của tuần), {@code identifier} (phong/donvi/coquan),
 * {@code userCreator} (canhan + audit "ai thao tác" cho mọi scope), {@code note} (Domino
 * {@code ghichu}).
 *
 * <p><b>Field động</b>: {@code weekFieldsJson} (cột {@code week_fields} JSONB) — giữ NGUYÊN key
 * Domino ({@code t2_s}, {@code t2_s_gio}, {@code t2_s_dd}, {@code t2_s_tp}, {@code t2_s_1}, ...)
 * để khớp 100% contract FE (FE đọc thẳng {@code resdata.t2_s}) — xem
 * {@link com.td.application.workcalendars.SaveWorkCalendarUseCase#WEEK_FIELD_KEY_PATTERN}.
 */
@Entity
@Table(name = "work_calendars")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkCalendar extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(nullable = false, length = 20)
    private String scope;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(length = 100)
    private String identifier;

    @Column(name = "user_creator", length = 100)
    private String userCreator;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "week_fields", columnDefinition = "jsonb", nullable = false)
    private String weekFieldsJson = "{}";

    public WorkCalendar(String scope, LocalDate weekStart, String identifier, String userCreator, String note) {
        this.id = UUID.randomUUID();
        this.scope = scope;
        this.weekStart = weekStart;
        this.identifier = identifier;
        this.userCreator = userCreator;
        this.note = note;
    }

    // Chi cho sua ghichu khi update - scope/weekStart/identifier/userCreator la chu the + tuan CO
    // DINH tu luc tao (dung hanh vi Domino - FE luon goi lai dung viewname/subject khi sua, khong co
    // luong nghiep vu nao "doi chu lich"/"doi tuan" cua 1 ban ghi da co).
    public WorkCalendar updateNote(String note) {
        if (note != null) {
            this.note = note;
        }
        return this;
    }

    // Ghi de TOAN BO field dong (JSONB) - KHONG merge (khac Category/Organization) - dung yeu cau
    // nghiep vu that (muc 7.6.2 tai lieu 09): FE luon gui nguyen luoi tuan, o bi xoa gui gia tri "".
    public WorkCalendar replaceWeekFields(String weekFieldsJson) {
        this.weekFieldsJson = (weekFieldsJson == null || weekFieldsJson.isBlank()) ? "{}" : weekFieldsJson;
        return this;
    }
}

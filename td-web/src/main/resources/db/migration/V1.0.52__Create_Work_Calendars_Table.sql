-- Port module "Lich cong tac" (lichct) Domino -> Java, 08/09/2026 - xem
-- docs/analysis/09-phan-tich-port-lich-cong-tac-domino-sang-java.md muc 7.2.
--
-- Bang vat ly DOC LAP work_calendars (KHONG dung chung "documents") - 1 dong = 1 tuan cua 1 chu the
-- (scope: canhan|phong|donvi|coquan - "lanhdaotinh" phia FE duoc luu nhu "coquan" tren CHINH tenant
-- hien tai, xem javadoc GetWeekWorkCalendarUseCase/SaveWorkCalendarUseCase ve quyet dinh bao mat nay).
-- Cung kien truc BL-101 (Dynamic Data Engine) cho luoi o lich dong (week_fields JSONB, giu nguyen key
-- Domino: t2_s, t2_s_gio, t2_s_dd, t2_s_tp, t2_s_1, ...) - cung tien le duthao_documents/vbcddh_documents.
CREATE TABLE work_calendars (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    scope            VARCHAR(20)  NOT NULL,                          -- canhan|phong|donvi|coquan
    week_start       DATE         NOT NULL,                          -- thu2 cua tuan
    identifier       VARCHAR(100),                                   -- phong/donvi/coquan
    user_creator     VARCHAR(100),                                   -- canhan (+ audit nguoi thao tac)
    note             TEXT,                                           -- ghichu
    week_fields      JSONB        NOT NULL DEFAULT '{}'::jsonb,      -- t2_s, t2_s_gio, t2_s_dd, ...
    created_by       UUID,
    created_on       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on       TIMESTAMP WITH TIME ZONE,
    deleted_by       UUID
);

-- 1 tuan / 1 chu the: voi scope != 'canhan' chu the la "identifier" (phong/donvi/coquan); voi
-- scope = 'canhan' chu the la "user_creator" (username). 2 index rieng vi khong the gop 1 unique
-- index tren ca 2 cot (mot cot luon NULL tuy scope).
CREATE UNIQUE INDEX UX_work_calendars_scope_week_owner
    ON work_calendars(scope, week_start, identifier)
    WHERE deleted_on IS NULL AND scope <> 'canhan';

CREATE UNIQUE INDEX UX_work_calendars_scope_week_user
    ON work_calendars(scope, week_start, user_creator)
    WHERE deleted_on IS NULL AND scope = 'canhan';

CREATE INDEX IX_work_calendars_week_start ON work_calendars(week_start);
CREATE INDEX IX_work_calendars_week_fields_gin ON work_calendars USING GIN(week_fields);

COMMENT ON TABLE work_calendars IS 'Lich cong tac (Domino lichct.nsf, Form="lich") - 1 dong = 1 tuan cua 1 chu the (canhan/phong/donvi/coquan) - port theo docs/analysis/09-phan-tich-port-lich-cong-tac-domino-sang-java.md';
COMMENT ON COLUMN work_calendars.week_fields IS 'Luoi o lich dong (t2_s, t2_s_gio, t2_s_dd, t2_s_tp, t2_s_1, ...) - BL-101 Dynamic Data Engine, ghi de TOAN BO khi luu (khong merge tung o) - xem SaveWorkCalendarUseCase';

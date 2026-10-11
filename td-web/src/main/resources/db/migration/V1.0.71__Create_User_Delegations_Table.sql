-- 09/10/2026 - port chuc nang "Uy quyen" (menu nguoi dung -> Uy quyen) tu Domino sang Java.
-- Domino luu 1 chuoi JSON delegation_obj tren ban ghi nguoi dung roi CHEP sang to chuc (nguoi dai dien
-- nhan van ban), nhom nhan van ban, so van ban. Java luu BANG RIENG (moi lan uy quyen 1 dong, co lich su,
-- huy duoc) va cac API chon nguoi/to chuc/nhom/so tu gan uy quyen DANG HIEU LUC luc doc (khong chep).
--
-- Giong app_users/organizations/identity_file_metadata: bang ton tai trong schema MOI tenant (Flyway
-- chay dong bo) nhung ung dung CHI doc/ghi qua identityDataSource (UserDelegation nam trong
-- com.td.domain.sharedcore - persistence unit "identity").
CREATE TABLE user_delegations (
    id                 UUID         PRIMARY KEY,
    delegator_user_id  UUID         NOT NULL,
    delegator_username VARCHAR(100) NOT NULL,
    delegate_user_id   UUID         NOT NULL,
    delegate_username  VARCHAR(100) NOT NULL,
    delegate_name      VARCHAR(300),
    delegate_position  VARCHAR(300),
    start_date         DATE         NOT NULL,
    end_date           DATE         NOT NULL,
    scopes             VARCHAR(200) NOT NULL,
    subject            TEXT,
    status             VARCHAR(20)  NOT NULL DEFAULT 'active',
    revoked_on         TIMESTAMP WITH TIME ZONE,
    revoked_by         UUID,
    dynamic_fields     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_by         UUID,
    created_on         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by   UUID,
    last_modified_on   TIMESTAMP WITH TIME ZONE,
    deleted_on         TIMESTAMP WITH TIME ZONE,
    deleted_by         UUID,
    CONSTRAINT FK_user_delegations_delegator FOREIGN KEY (delegator_user_id) REFERENCES app_users(id),
    CONSTRAINT FK_user_delegations_delegate FOREIGN KEY (delegate_user_id) REFERENCES app_users(id),
    CONSTRAINT CK_user_delegations_dates CHECK (end_date >= start_date)
);

CREATE INDEX idx_user_delegations_delegator ON user_delegations(delegator_user_id);
CREATE INDEX idx_user_delegations_delegator_username ON user_delegations(lower(delegator_username));
CREATE INDEX idx_user_delegations_delegate ON user_delegations(delegate_user_id);
CREATE INDEX idx_user_delegations_period ON user_delegations(start_date, end_date) WHERE status = 'active' AND deleted_on IS NULL;

COMMENT ON TABLE user_delegations IS
    'Uy quyen xu ly thay (menu Uy quyen). Doc/ghi qua identityDataSource. Hieu luc = status active, chua xoa, start_date <= hom nay <= end_date (tinh ca 2 dau).';
COMMENT ON COLUMN user_delegations.scopes IS
    'Pham vi uy quyen noi ";" - vbden (van ban den), duthao (du thao). Giong delegation_obj.data cua Domino.';
COMMENT ON COLUMN user_delegations.status IS
    'active | revoked (nguoi uy quyen huy hoac tao uy quyen moi thay the).';

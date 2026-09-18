-- BL-118 (bugfix NGHIEM TRONG, 20/08/2026): bang cache "username -> tenant vat ly" (V6) khong con
-- can thiet - xac nhan qua doi chieu database that (tai khoan "skhcn"): AppUser/Organization/RoleGroup
-- la "danh muc dung chung", KHONG nhan ban theo tung tenant, luon song CO DINH o 1 database duy nhat
-- (identityEntityManagerFactory, xem MultiPersistenceUnitJpaConfig) - khong con bai toan "ga-va-trung"
-- can quet qua tung tenant nghiep vu de "tim" AppUser nua, nen khong con can cache ket qua quet.

DROP TABLE IF EXISTS user_tenant_locations;

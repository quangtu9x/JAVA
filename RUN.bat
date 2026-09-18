@echo off
REM Bugfix 03/09/2026 (bao loi nguoi dung: sua code, build+restart nhung khong ra hieu luc, phai
REM build lai lan nua moi thay - dieu tra nghi ngo cao nhat la do lenh build): script cu chi chay
REM "mvn spring-boot:run -pl td-web" - KHONG co "-am" nen 3 module con lai (td-application/td-domain/
REM td-infrastructure - noi hau het logic nghiep vu song, vd ProcessDiagramService/TenantRoutingDataSource/
REM CurrentUserContextResolver) KHONG duoc dua vao reactor build, Maven se lay JAR CU cua chung tu
REM ~/.m2 (co the rat cu, tu lan "mvn install" gan nhat) thay vi bien dich lai tu source moi nhat -
REM sua code nhung khong bao gio thay hieu luc qua script nay. Them "-am clean install" truoc, dung
REM "run-dev.ps1" (da sua cung ngay) de dam bao build dung.
mvn -pl td-web -am clean install -DskipTests -q
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Build that bai, dung lai truoc khi spring-boot:run.
    exit /b %ERRORLEVEL%
)
mvn spring-boot:run -pl td-web

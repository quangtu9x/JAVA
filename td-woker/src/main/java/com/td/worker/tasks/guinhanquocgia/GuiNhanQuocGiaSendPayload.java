package com.td.worker.tasks.guinhanquocgia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Payload task {@code guinhanquocgia.send} — bổ sung 09/09/2026.
 *
 * <p>THAM KHẢO (không copy nguyên) field header đã dùng ở
 * {@code docs/td-guinhanquocgia/src/main/java/com/vpcp/example/ServiceTest.java} case 7 (jsonHeader
 * {@code from}/{@code servicetype}/{@code messagetype}) và {@code docs/07-mapping-du-lieu-postgresql.md}
 * (cột {@code vpcp_edoc_outbox}).</p>
 *
 * <p><b>Giới hạn CỐ Ý của bản scaffold này</b> (🔴 xem checklist mục "Việc còn lại"): payload giả
 * định nội dung {@code .edxml} ĐÃ ĐƯỢC BUILD SẴN (base64) — bước "build eDXML từ dữ liệu nghiệp vụ
 * thật" (ánh xạ tổ chức/người ký/chữ ký số — xem {@code EdXMLBuild.java} tham khảo) CHƯA làm, vì
 * việc ánh xạ field nghiệp vụ (tổ chức/người ký) với dữ liệu {@code documents}/{@code categories}
 * của td-web CHƯA được chốt (đúng ghi nhận ở {@code docs/td-guinhanquocgia/docs/09-rui-ro...}).</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GuiNhanQuocGiaSendPayload {

    /** Id văn bản nội bộ (td-web) — dùng để đối chiếu/tra cứu, KHÔNG phải doc_id của Trung tâm. */
    private String documentId;

    /** Mã đơn vị GỬI (OrganId) — dùng dựng jsonHeader {@code from}. */
    private String senderAgencyCode;

    /** Mã đơn vị NHẬN (OrganId) — đơn vị liên thông đích. */
    private String recipientAgencyCode;

    /** 1 trong 4 giá trị {@code GuiNhanQuocGiaTaskConstants.DOC_TYPE_*} (BussinessDocType SDK). */
    private String docType;

    private String subject;

    /**
     * Nội dung file {@code .edxml} ĐÃ ĐƯỢC BUILD SẴN, mã hoá base64 — xem giới hạn ở javadoc lớp.
     */
    private String edxmlBase64;
}

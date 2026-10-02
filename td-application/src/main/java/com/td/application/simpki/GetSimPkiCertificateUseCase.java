package com.td.application.simpki;

import com.td.application.common.models.Result;

/** Tra cứu chứng thư số (X.509) của thuê bao SIM-PKI theo MSISDN (đồng bộ, không ký). */
public interface GetSimPkiCertificateUseCase {
    Result<SimPkiCertificateResult> execute(String msisdn);
}

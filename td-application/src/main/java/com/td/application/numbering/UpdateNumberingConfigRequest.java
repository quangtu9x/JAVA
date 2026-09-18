package com.td.application.numbering;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateNumberingConfigRequest {

    @NotBlank(message = "format không được để trống")
    private String format;

    private Boolean active;
}

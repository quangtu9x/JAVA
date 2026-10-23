package com.td.application.vbden;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Request đọc cây thống kê của hai Domino view theonguoichutri/theodonvichutri. */
@Data
public class VbdenStatisticsGroupRequest {

    @NotBlank(message = "viewname khong duoc de trong")
    private String viewname;

    private String node = "#";
    private boolean getallchild;
    private String items = "getall";
    private int pageNumber = 1;
    private int pageSize = 20;
}

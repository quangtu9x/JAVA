package com.td.application.storage;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.InputStream;

@Data
@AllArgsConstructor
public class DownloadFileResponse {
    private InputStream inputStream;
    private String filename;
    private String contentType;
    private Long fileSize;
}
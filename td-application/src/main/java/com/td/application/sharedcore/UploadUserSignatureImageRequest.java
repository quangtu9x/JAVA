package com.td.application.sharedcore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadUserSignatureImageRequest {

    private UUID userId;
    private UserSignatureImageType type;
    private InputStream fileContent;
    private String fileName;
    private String mimeType;
    private Long fileSize;
}

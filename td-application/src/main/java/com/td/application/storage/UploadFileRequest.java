package com.td.application.storage;

import com.td.domain.storage.FileCategory;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadFileRequest {
    
    @NotNull(message = "File cannot be null")
    private MultipartFile file;
    
    @NotNull(message = "File category cannot be null")
    private FileCategory fileCategory;
    
    private String description;
    
    private String tags;
    
    private Boolean isPublic = false;
    
    private UUID uploadedBy;
}
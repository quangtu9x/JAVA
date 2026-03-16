package com.td.application.storage;

import com.td.domain.storage.FileMetadata;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-03-16T11:59:53+0700",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 25.0.1 (Eclipse Adoptium)"
)
@Component
public class FileMetadataMapperImpl implements FileMetadataMapper {

    @Override
    public FileMetadataDto toDto(FileMetadata entity) {
        if ( entity == null ) {
            return null;
        }

        FileMetadataDto fileMetadataDto = new FileMetadataDto();

        fileMetadataDto.setId( entity.getId() );
        fileMetadataDto.setOriginalFilename( entity.getOriginalFilename() );
        fileMetadataDto.setStoredFilename( entity.getStoredFilename() );
        fileMetadataDto.setFilePath( entity.getFilePath() );
        fileMetadataDto.setFileSize( entity.getFileSize() );
        fileMetadataDto.setContentType( entity.getContentType() );
        fileMetadataDto.setFileExtension( entity.getFileExtension() );
        fileMetadataDto.setFileCategory( entity.getFileCategory() );
        fileMetadataDto.setBucketName( entity.getBucketName() );
        fileMetadataDto.setUploadedBy( entity.getUploadedBy() );
        fileMetadataDto.setUploadedAt( entity.getUploadedAt() );
        fileMetadataDto.setDownloadCount( entity.getDownloadCount() );
        fileMetadataDto.setLastDownloadedAt( entity.getLastDownloadedAt() );
        fileMetadataDto.setIsPublic( entity.getIsPublic() );
        fileMetadataDto.setDescription( entity.getDescription() );
        fileMetadataDto.setTags( entity.getTags() );

        fileMetadataDto.setHumanReadableSize( entity.getHumanReadableSize() );
        fileMetadataDto.setImage( entity.isImage() );
        fileMetadataDto.setPdf( entity.isPdf() );
        fileMetadataDto.setDocument( entity.isDocument() );

        return fileMetadataDto;
    }

    @Override
    public FileMetadata toEntity(FileMetadataDto dto) {
        if ( dto == null ) {
            return null;
        }

        FileMetadata fileMetadata = new FileMetadata();

        fileMetadata.setId( dto.getId() );
        fileMetadata.setOriginalFilename( dto.getOriginalFilename() );
        fileMetadata.setStoredFilename( dto.getStoredFilename() );
        fileMetadata.setFilePath( dto.getFilePath() );
        fileMetadata.setFileSize( dto.getFileSize() );
        fileMetadata.setContentType( dto.getContentType() );
        fileMetadata.setFileExtension( dto.getFileExtension() );
        fileMetadata.setFileCategory( dto.getFileCategory() );
        fileMetadata.setBucketName( dto.getBucketName() );
        fileMetadata.setUploadedBy( dto.getUploadedBy() );
        fileMetadata.setUploadedAt( dto.getUploadedAt() );
        fileMetadata.setDownloadCount( dto.getDownloadCount() );
        fileMetadata.setLastDownloadedAt( dto.getLastDownloadedAt() );
        fileMetadata.setIsPublic( dto.getIsPublic() );
        fileMetadata.setDescription( dto.getDescription() );
        fileMetadata.setTags( dto.getTags() );

        return fileMetadata;
    }
}

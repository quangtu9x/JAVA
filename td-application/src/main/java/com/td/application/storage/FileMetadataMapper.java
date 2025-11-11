package com.td.application.storage;

import com.td.domain.storage.FileMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface FileMetadataMapper {
    
    FileMetadataMapper INSTANCE = Mappers.getMapper(FileMetadataMapper.class);
    
    @Mapping(target = "humanReadableSize", expression = "java(entity.getHumanReadableSize())")
    @Mapping(target = "isImage", expression = "java(entity.isImage())")
    @Mapping(target = "isPdf", expression = "java(entity.isPdf())")
    @Mapping(target = "isDocument", expression = "java(entity.isDocument())")
    @Mapping(target = "downloadUrl", ignore = true) // Set separately
    FileMetadataDto toDto(FileMetadata entity);
    
    FileMetadata toEntity(FileMetadataDto dto);
}
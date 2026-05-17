package com.assetiq.dto;

import com.assetiq.enums.AttachmentEntityType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentAttachmentDto {
    private UUID id;
    private AttachmentEntityType entityType;
    private UUID entityId;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private String uploadedByName;
    private Instant createdAt;
    /** Short-lived presigned URL for viewing/downloading — populated on demand, not stored. */
    private String downloadUrl;
}

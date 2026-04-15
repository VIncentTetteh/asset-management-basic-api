package com.assetiq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class WebhookDto {

    private UUID id;

    @NotBlank
    @Size(max = 200)
    private String name;

    @NotBlank
    @Size(max = 2048)
    private String url;

    /** Event names to subscribe to. */
    private List<String> events;

    private boolean active = true;

    /** Only returned on create; never on list/get. */
    private String secret;

    private long deliveryCount;
    private long failureCount;
    private Instant lastTriggeredAt;
    private Instant createdAt;
}

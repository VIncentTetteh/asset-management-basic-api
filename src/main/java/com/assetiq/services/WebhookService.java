package com.assetiq.services;

import com.assetiq.dto.WebhookDeliveryDto;
import com.assetiq.dto.WebhookDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface WebhookService {

    WebhookDto create(WebhookDto dto);

    List<WebhookDto> list();

    WebhookDto getById(UUID id);

    WebhookDto update(UUID id, WebhookDto dto);

    void delete(UUID id);

    /** Fires an event to all active webhooks subscribed to eventName. */
    void dispatch(String eventName, Map<String, Object> data);

    /** Test-fire a webhook and return a synthetic delivery record. */
    WebhookDeliveryDto test(UUID webhookId);

    Page<WebhookDeliveryDto> listDeliveries(UUID webhookId, String status, Pageable pageable);

    WebhookDeliveryDto getDelivery(UUID webhookId, UUID deliveryId);
}

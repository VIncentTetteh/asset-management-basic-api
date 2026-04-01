package com.example.demo.controllers.v1;

import com.example.demo.dto.WebhookDeliveryDto;
import com.example.demo.dto.WebhookDto;
import com.example.demo.dto.PagedResponseDto;
import com.example.demo.services.WebhookService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
@PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
public class WebhooksController {

    private final WebhookService webhookService;

    public WebhooksController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /** POST /api/v1/webhooks */
    @PostMapping
    public ResponseEntity<WebhookDto> create(@Valid @RequestBody WebhookDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(webhookService.create(dto));
    }

    /** GET /api/v1/webhooks */
    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(webhookService.list());
    }

    /** GET /api/v1/webhooks/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<WebhookDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(webhookService.getById(id));
    }

    /** PATCH /api/v1/webhooks/{id} */
    @PatchMapping("/{id}")
    public ResponseEntity<WebhookDto> update(@PathVariable UUID id, @RequestBody WebhookDto dto) {
        return ResponseEntity.ok(webhookService.update(id, dto));
    }

    /** DELETE /api/v1/webhooks/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        webhookService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/v1/webhooks/{id}/test */
    @PostMapping("/{id}/test")
    public ResponseEntity<WebhookDeliveryDto> test(@PathVariable UUID id) {
        return ResponseEntity.ok(webhookService.test(id));
    }

    /** GET /api/v1/webhooks/{id}/deliveries */
    @GetMapping("/{id}/deliveries")
    public ResponseEntity<PagedResponseDto<WebhookDeliveryDto>> listDeliveries(
            @PathVariable UUID id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long offset,
            @PageableDefault(size = 20) Pageable pageable) {
        int effectiveLimit = (limit != null && limit > 0) ? limit : pageable.getPageSize();
        long effectiveOffset = (offset != null && offset >= 0)
                ? offset
                : (long) pageable.getPageNumber() * effectiveLimit;

        Pageable effectivePageable = PageRequest.of((int) (effectiveOffset / effectiveLimit), effectiveLimit, pageable.getSort());
        Page<WebhookDeliveryDto> page = webhookService.listDeliveries(id, status, effectivePageable);

        PagedResponseDto<WebhookDeliveryDto> response = new PagedResponseDto<>();
        response.setTotal(page.getTotalElements());
        response.setLimit(effectiveLimit);
        response.setOffset(effectiveOffset);
        response.setItems(page.getContent());
        return ResponseEntity.ok(response);
    }

    /** GET /api/v1/webhooks/{id}/deliveries/{deliveryId} */
    @GetMapping("/{id}/deliveries/{deliveryId}")
    public ResponseEntity<WebhookDeliveryDto> getDelivery(
            @PathVariable UUID id,
            @PathVariable UUID deliveryId) {
        return ResponseEntity.ok(webhookService.getDelivery(id, deliveryId));
    }
}

package com.example.demo.services.impl;

import com.example.demo.dto.VendorPerformanceReviewDto;
import com.example.demo.models.Organisation;
import com.example.demo.models.Supplier;
import com.example.demo.models.VendorPerformanceReview;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.repositories.SupplierRepository;
import com.example.demo.repositories.UserRepository;
import com.example.demo.repositories.VendorPerformanceReviewRepository;
import com.example.demo.services.TenantAwareService;
import com.example.demo.services.VendorPerformanceService;
import jakarta.transaction.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VendorPerformanceServiceImpl extends TenantAwareService implements VendorPerformanceService {

    private final VendorPerformanceReviewRepository reviewRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;

    public VendorPerformanceServiceImpl(OrganisationRepository organisationRepository,
                                        VendorPerformanceReviewRepository reviewRepository,
                                        SupplierRepository supplierRepository,
                                        UserRepository userRepository) {
        super(organisationRepository);
        this.reviewRepository = reviewRepository;
        this.supplierRepository = supplierRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public VendorPerformanceReviewDto create(VendorPerformanceReviewDto dto) {
        Organisation org = requireTenantOrg();
        Supplier supplier = supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getSupplierId(), org)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + dto.getSupplierId()));

        VendorPerformanceReview review = new VendorPerformanceReview();
        review.setSupplier(supplier);
        review.setRating(dto.getRating());
        review.setDeliveryScore(dto.getDeliveryScore());
        review.setQualityScore(dto.getQualityScore());
        review.setSupportScore(dto.getSupportScore());
        review.setFeedback(dto.getFeedback());
        review.setPeriodStart(dto.getPeriodStart());
        review.setPeriodEnd(dto.getPeriodEnd());
        review.setOrganisation(org);

        // Resolve the reviewer from the authenticated principal
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            userRepository.findByEmailAndOrganisationId(auth.getName(), org.getId())
                    .ifPresent(review::setReviewedBy);
        }

        return toDto(reviewRepository.save(review));
    }

    @Override
    public VendorPerformanceReviewDto getById(UUID id) {
        Organisation org = requireTenantOrg();
        return toDto(reviewRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Review not found: " + id)));
    }

    @Override
    public List<VendorPerformanceReviewDto> listAll() {
        Organisation org = requireTenantOrg();
        return reviewRepository.findByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(org)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public List<VendorPerformanceReviewDto> listBySupplier(UUID supplierId) {
        Organisation org = requireTenantOrg();
        supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(supplierId, org)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + supplierId));
        return reviewRepository.findBySupplierIdAndDeletedAtIsNull(supplierId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getSupplierSummary(UUID supplierId) {
        Organisation org = requireTenantOrg();
        Supplier supplier = supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(supplierId, org)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + supplierId));

        List<VendorPerformanceReview> reviews = reviewRepository.findBySupplierIdAndDeletedAtIsNull(supplierId);
        Double avg = reviewRepository.averageRatingBySupplierId(supplierId);

        return Map.of(
                "supplierId", supplierId,
                "supplierName", supplier.getName(),
                "totalReviews", reviews.size(),
                "averageRating", avg != null ? Math.round(avg * 100.0) / 100.0 : 0.0
        );
    }

    @Override
    @Transactional
    public VendorPerformanceReviewDto update(UUID id, VendorPerformanceReviewDto dto) {
        Organisation org = requireTenantOrg();
        VendorPerformanceReview review = reviewRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Review not found: " + id));

        if (dto.getRating() != null) review.setRating(dto.getRating());
        if (dto.getDeliveryScore() != null) review.setDeliveryScore(dto.getDeliveryScore());
        if (dto.getQualityScore() != null) review.setQualityScore(dto.getQualityScore());
        if (dto.getSupportScore() != null) review.setSupportScore(dto.getSupportScore());
        if (dto.getFeedback() != null) review.setFeedback(dto.getFeedback());
        if (dto.getPeriodStart() != null) review.setPeriodStart(dto.getPeriodStart());
        if (dto.getPeriodEnd() != null) review.setPeriodEnd(dto.getPeriodEnd());

        return toDto(reviewRepository.save(review));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Organisation org = requireTenantOrg();
        VendorPerformanceReview review = reviewRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Review not found: " + id));
        review.setDeletedAt(Instant.now());
        reviewRepository.save(review);
    }

    private VendorPerformanceReviewDto toDto(VendorPerformanceReview r) {
        VendorPerformanceReviewDto d = new VendorPerformanceReviewDto();
        d.setId(r.getId());
        d.setRating(r.getRating());
        d.setDeliveryScore(r.getDeliveryScore());
        d.setQualityScore(r.getQualityScore());
        d.setSupportScore(r.getSupportScore());
        d.setFeedback(r.getFeedback());
        d.setPeriodStart(r.getPeriodStart());
        d.setPeriodEnd(r.getPeriodEnd());
        if (r.getSupplier() != null) {
            d.setSupplierId(r.getSupplier().getId());
            d.setSupplierName(r.getSupplier().getName());
        }
        if (r.getReviewedBy() != null) {
            d.setReviewedById(r.getReviewedBy().getId());
            d.setReviewedByEmail(r.getReviewedBy().getEmail());
        }
        return d;
    }
}

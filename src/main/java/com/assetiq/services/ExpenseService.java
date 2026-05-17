package com.assetiq.services;

import com.assetiq.dto.ExpenseDto;
import com.assetiq.dto.ExpenseFilterRequest;
import com.assetiq.dto.PagedResponseDto;

import java.util.List;
import java.util.UUID;

public interface ExpenseService {

    /** Submit a new expense (sets status to SUBMITTED). */
    ExpenseDto submit(ExpenseDto dto);

    /** Approve an expense; auto-deducts the amount from the linked budget if one is set. */
    ExpenseDto approve(UUID id);

    /** Reject an expense with an optional reason. */
    ExpenseDto reject(UUID id, String reason);

    ExpenseDto getById(UUID id);

    List<ExpenseDto> listAll();

    /** Returns only SUBMITTED expenses awaiting approval. */
    List<ExpenseDto> listPending();

    List<ExpenseDto> listByUser(UUID userId);

    /** Returns a filtered, paged list of expenses. */
    PagedResponseDto<ExpenseDto> listPaged(ExpenseFilterRequest req);

    void delete(UUID id);
}

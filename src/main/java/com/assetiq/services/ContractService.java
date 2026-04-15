package com.assetiq.services;

import com.assetiq.dto.ContractDto;

import java.util.List;
import java.util.UUID;

public interface ContractService {

    ContractDto create(ContractDto dto);

    ContractDto getById(UUID id);

    List<ContractDto> listAll();

    List<ContractDto> listExpiringSoon(int days);

    ContractDto update(UUID id, ContractDto dto);

    ContractDto patch(UUID id, ContractDto dto);

    void delete(UUID id);
}

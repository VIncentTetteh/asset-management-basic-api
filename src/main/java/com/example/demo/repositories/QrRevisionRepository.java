package com.example.demo.repositories;

import com.example.demo.models.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface QrRevisionRepository extends JpaRepository<QrRevision, UUID> {
    List<QrRevision> findByAssetOrderByVersionDesc(Asset asset);
    Optional<QrRevision> findTopByAssetOrderByVersionDesc(Asset asset);
}

package com.assetiq.models;

import com.assetiq.enums.DepartmentStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;



import java.math.BigDecimal;
import java.util.Set;

@Entity
@Table(name = "department")
@Data
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Department extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private String departmentCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_department_id")
    private Department parentDepartment;

    @OneToMany(mappedBy = "parentDepartment")
    private Set<Department> subDepartments;

    @ManyToOne(fetch = FetchType.LAZY)
    private User manager;

    @Column
    private String costCenterCode;

    private BigDecimal budgetLimit;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DepartmentStatus status = DepartmentStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Organisation organisation;

    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL)
    private Set<Asset> assets;

    @OneToMany(mappedBy = "department")
    private Set<User> users;

}

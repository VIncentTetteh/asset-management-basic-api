package com.example.demo.models;

import com.example.demo.enums.DepartmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "department")
public class Department extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String departmentCode;

    @ManyToOne
    @JoinColumn(name = "parent_department_id")
    private Department parentDepartment;

    @OneToMany(mappedBy = "parentDepartment")
    private Set<Department> subDepartments;

    @ManyToOne
    private User manager;

    @Column(unique = true)
    private String costCenterCode;

    private BigDecimal budgetLimit;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DepartmentStatus status = DepartmentStatus.ACTIVE;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Organisation organisation;

    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL)
    private Set<Asset> assets;

    @OneToMany(mappedBy = "department")
    private Set<User> users;

}

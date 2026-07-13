package com.assetiq.models;

import com.assetiq.enums.EmployeeStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * HR-level record of a person who can hold assets. Distinct from {@link User}:
 * not every employee has (or needs) a system login. When they do, {@code user}
 * links the two so checkouts made to that user surface on the employee.
 */
@Entity
@Table(name = "employee")
@Getter
@Setter
public class Employee extends BaseEntity {

    @Column(name = "employee_number", length = 100)
    private String employeeNumber;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column
    private String email;

    @Column(length = 100)
    private String phone;

    @Column(name = "job_title")
    private String jobTitle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private EmployeeStatus status = EmployeeStatus.ONBOARDING;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}

package com.example.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Setter
@Getter
@Entity
public class Department extends BaseEntity {

    private String name;

    @ManyToOne
    private Organisation organisation;

    @OneToMany(mappedBy = "department")
    private Set<Asset> assets;

}

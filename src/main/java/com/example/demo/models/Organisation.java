package com.example.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Entity
public class Organisation extends BaseEntity {

    private String name;

    @OneToMany(mappedBy = "organisation")
    private Set<Department> departments;

}

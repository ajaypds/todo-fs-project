package com.example.todo.label.entity;

import com.example.todo.task.entity.Task;
import com.example.todo.user.entity.User;

import jakarta.persistence.*;

import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "labels")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Label {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String color;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private User user;

    @ManyToMany(mappedBy = "labels")
    private Set<Task> tasks = new HashSet<>();
}
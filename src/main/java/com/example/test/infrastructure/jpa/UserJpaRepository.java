package com.example.test.infrastructure.jpa;

import com.example.test.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);
}

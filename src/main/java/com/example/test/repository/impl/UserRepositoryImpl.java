package com.example.test.repository.impl;

import com.example.test.infrastructure.jpa.UserJpaRepository;
import com.example.test.model.User;
import com.example.test.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }
}

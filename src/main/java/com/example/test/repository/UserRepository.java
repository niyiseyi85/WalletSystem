package com.example.test.repository;

import com.example.test.model.User;

public interface UserRepository {

    User save(User user);

    boolean existsByEmail(String email);
}

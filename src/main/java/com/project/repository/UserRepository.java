package com.project.repository;

import com.project.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findBySessionToken(String sessionToken);

    Optional<User> findByResetPasswordToken(String resetPasswordToken);
}

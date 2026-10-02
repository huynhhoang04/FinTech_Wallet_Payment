package com.fintech.wallet.ledger.repository;

import com.fintech.wallet.ledger.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByPhone(String phone);

	boolean existsByPhone(String phone);

	boolean existsByEmail(String email);
}

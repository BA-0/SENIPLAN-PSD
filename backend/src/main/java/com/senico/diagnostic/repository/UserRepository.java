package com.senico.diagnostic.repository;

import com.senico.diagnostic.domain.Role;
import com.senico.diagnostic.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    /** Comptes d'un role rattaches a une direction (suppression de la direction : ses chefs de groupe partent avec elle). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from User u where u.group.id = :groupId and u.role = :role")
    int deleteAllByGroupIdAndRole(Long groupId, Role role);

    /** Detache de la direction les comptes qui lui survivent (admin, DG). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.group = null where u.group.id = :groupId")
    int detachAllFromGroup(Long groupId);
}

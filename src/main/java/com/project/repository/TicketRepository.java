package com.project.repository;

import com.project.models.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findAllByOrderByCreatedAtDesc();

    List<Ticket> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    Optional<Ticket> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("""
            select t
            from Ticket t
            where (:ownerId is null or t.owner.id = :ownerId)
              and (
                    :term is null
                    or lower(t.title) like lower(concat('%', :term, '%'))
                    or lower(t.description) like lower(concat('%', :term, '%'))
                    or lower(t.severity) like lower(concat('%', :term, '%'))
                  )
            order by t.updatedAt desc, t.createdAt desc
            """)
    List<Ticket> search(@Param("ownerId") UUID ownerId, @Param("term") String term);
}

package com.example.liquibase.repository;

import com.example.liquibase.domain.Competition;
import com.example.liquibase.domain.enums.SpeciesType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompetitionRepository extends JpaRepository<Competition, Integer> {
    Optional<Competition> getById(UUID id);

    Optional<Competition> findByCode(String code);

    Optional<Competition> findByLocation(String location);

    List<Competition> findByDateBetween(LocalDateTime sevenDaysAgo, LocalDateTime currentDate);

    List<Competition> findByOpenRegistrationTrueAndDateBetween(LocalDateTime start, LocalDateTime end);

    List<Competition> findByDateAfter(LocalDateTime date);

}

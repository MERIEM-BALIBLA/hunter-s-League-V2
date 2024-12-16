package com.example.liquibase.service.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipationDTO {
    private UUID id;
    private String userName;
    private String competitionLocation;
    private Double score;
}

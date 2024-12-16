package com.example.liquibase.service.implementations;

import com.example.liquibase.domain.Competition;
import com.example.liquibase.domain.Participation;
import com.example.liquibase.domain.User;
import com.example.liquibase.domain.enums.Role;
import com.example.liquibase.repository.ParticipationRepository;
import com.example.liquibase.service.DTO.ParticipationDTO;
import com.example.liquibase.service.DTO.UserParticipationDTO;
import com.example.liquibase.service.DTO.mapper.ParticipationMapper;
import com.example.liquibase.service.DTO.mapper.UserParticipationMapper;
import com.example.liquibase.service.interfaces.ParticipationInterface;
import com.example.liquibase.web.exception.participation.ParticipationException;
import com.example.liquibase.web.exception.user.UserException;
import com.example.liquibase.web.vm.ParticipationVM;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ParticipationService implements ParticipationInterface {

    private final ParticipationRepository participationRepository;
    private final ParticipationMapper participationMapper;
    private final CompetitionService competitionService;
    private final UserService userService;
    private final UserParticipationMapper userParticipationMapper;

    public ParticipationService(ParticipationRepository participationRepository, ParticipationMapper participationMapper, CompetitionService competitionService, UserService userService, UserParticipationMapper userParticipationMapper) {
        this.participationRepository = participationRepository;
        this.participationMapper = participationMapper;
        this.competitionService = competitionService;
        this.userService = userService;
        this.userParticipationMapper = userParticipationMapper;
    }

    @Override
    public Page<Participation> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return participationRepository.findAllByOrderByIdDesc(pageable);
    }

    public int countUsersByCompetitionId(UUID id) {
        return participationRepository.countUsersByCompetitionId(id);
    }

    @Override
    public Participation save(ParticipationVM participationVM) {
        Optional<User> user = userService.getUserByName(participationVM.getUserName());
        Optional<Competition> competition = competitionService.getByCode(participationVM.getCompetitionCode());


        if (user.isPresent() && competition.isPresent()) {
            Optional<Participation> participationExists = existingParticipation(user.get().getId(), competition.get().getId());

            if (participationExists.isPresent()) {
                throw new ParticipationException("User has already participated in this competition");
            }

            long currentParticipants = countUsersByCompetitionId(competition.get().getId());
            if (currentParticipants >= competition.get().getMaxParticipants()) {
                throw new ParticipationException("The maximum number of participants has been reached for this competition");
            }

            if (!competition.get().getOpenRegistration()) {
                throw new ParticipationException("The date of the participation is limited");
            }

            if (user.get().getLicenseExpirationDate().isBefore(LocalDateTime.now())) {
                throw new ParticipationException("User's licence is expired");
            }

            Participation participation = participationMapper.toParticipation(participationVM);

            validateParticipation(participationVM.getUserName());

            participation.setUser(user.get());
            participation.setCompetition(competition.get());
            participation.setScore(0.);

            return participationRepository.save(participation);
        } else {
            throw new ParticipationException("There is already a participation with this profile");
        }
    }

    @Override
    public Optional<Participation> findById(UUID id) {
        return participationRepository.findById(id);
    }

    @Override
    public Participation update(Participation participation) {
        Optional<Participation> optionalParticipation = findById(participation.getId());
        validateParticipation(participation.getUser().getUsername());
        if (optionalParticipation.isPresent()) {
            Participation existingParticipation = optionalParticipation.get();
            existingParticipation.setCompetition(participation.getCompetition());
            existingParticipation.setUser(participation.getUser());
            existingParticipation.setScore(participation.getScore());

            return participationRepository.save(existingParticipation);
        } else {
            throw new RuntimeException("Participation not found");
        }
    }

    @Override
    public void delete(UUID id) {
        Optional<Participation> optionalParticipation = findById(id);
        if (optionalParticipation.isPresent()) {
            isAuthenticatedUserId(optionalParticipation.get().getUser().getId());
            participationRepository.delete(optionalParticipation.get());
        } else {
            throw new RuntimeException("Participation not found");
        }
    }

    //    Verify roles and authentication
    private void validateParticipation(String userName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Not authenticated.");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User userDetails) {
            Role userRole = userDetails.getRole();
            UUID authenticatedUserId = userDetails.getId();

            if (userRole.equals(Role.MEMBER) || userRole.equals(Role.JURY)) {
                Optional<User> targetUser = userService.getUserByName(userName);
                if (targetUser.isEmpty() || !targetUser.get().getId().equals(authenticatedUserId)) {
                    throw new AccessDeniedException("You can only create participation for yourself.");
                }
            }
        }
    }


    public boolean isAuthenticatedUserId(UUID id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof User userDetails) {
                UUID authenticatedUserId = userDetails.getId();
                return id.equals(authenticatedUserId);
            }
        }
        return false;
    }

    @Override
    public List<UserParticipationDTO> findByUserId(UUID id) {
        Optional<User> user = userService.getUserById(id);
        if (user.isEmpty()) {
            throw new ParticipationException("User with ID: " + id + " does not exist");
        }

        List<Participation> participations = participationRepository.findByUserId(id);
        if (participations.isEmpty()) {
            throw new ParticipationException("No participations found for user with ID: " + id);
        }


        if (!isAuthenticatedUserId(id)) {
            throw new UserException("you can only see yours participations");
        }

        return participations.stream()
                .map(userParticipationMapper::toDto)
                .collect(Collectors.toList());
    }

    public Optional<Participation> existingParticipation(UUID user_id, UUID competition_id) {
        return participationRepository.findParticipationByUserIdAndCompetitionId(user_id, competition_id);
    }

    public Participation updateScore(Participation participation, double score) {
        participation.setScore(participation.getScore());
        return participationRepository.save(participation);
    }

    @Override
    public List<ParticipationDTO> getTop3Participants() {
        List<Participation> topParticipations = participationRepository.findTop3ByOrderByScoreDesc();
        if (topParticipations.isEmpty()) {
            throw new ParticipationException("No participants found");
        }
        return participationMapper.toParticipationDTOs(topParticipations);
    }

}

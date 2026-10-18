package com.example.modauth.service;

import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.dto.PersonResponse;

import java.util.List;
import java.util.UUID;

/** Implements the "People" list + deactivate flow diagram (Team Leads and Brokers of the caller's brokerage). */
public interface PeopleService {

    List<PersonResponse> list(AuthenticatedUser admin);

    void deactivate(AuthenticatedUser admin, UUID userId);
}

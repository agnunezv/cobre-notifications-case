package com.cobre.notifications.adapter.in.web.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClientPrincipal(@NotBlank @Size(max = 64) String clientId) {}

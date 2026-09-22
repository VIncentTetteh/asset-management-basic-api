package com.assetiq.services.impl;

import com.assetiq.dto.TenantRegisterRequest;
import com.assetiq.models.Organisation;
import com.assetiq.models.Role;
import com.assetiq.models.SubscriptionPlan;
import com.assetiq.models.User;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.OrganisationSubscriptionRepository;
import com.assetiq.repositories.RoleRepository;
import com.assetiq.repositories.SubscriptionPlanRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.services.DefaultRoleSeederService;
import com.assetiq.services.EmailService;
import com.assetiq.services.EmailVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantRegistrationServiceImplTest {

    @Mock OrganisationRepository organisationRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserRepository userRepository;
    @Mock SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock OrganisationSubscriptionRepository organisationSubscriptionRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService emailService;
    @Mock EmailVerificationService emailVerificationService;
    @Mock DefaultRoleSeederService defaultRoleSeederService;

    private TenantRegistrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TenantRegistrationServiceImpl(organisationRepository, roleRepository, userRepository,
                subscriptionPlanRepository, organisationSubscriptionRepository, passwordEncoder, emailService,
                emailVerificationService, defaultRoleSeederService);
        ReflectionTestUtils.setField(service, "emailBaseUrl", "http://localhost:3000");
        when(organisationRepository.save(any(Organisation.class))).thenAnswer(inv -> {
            Organisation o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });
        when(roleRepository.findByNameAndOrganisationId(anyString(), any())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionPlanRepository.findByCodeAndDeletedAtIsNull("FREEMIUM")).thenReturn(Optional.of(new SubscriptionPlan()));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        // The email already exists in several tenants.
        when(userRepository.findAllByEmail(anyString()))
                .thenReturn(java.util.List.of(new User(), new User()));
    }

    private static TenantRegisterRequest request() {
        TenantRegisterRequest r = new TenantRegisterRequest();
        r.setOrganisationName("Acme");
        r.setAdminFirstName("Ama");
        r.setAdminLastName("Mensah");
        r.setAdminEmail("ama@example.com");
        r.setAdminPhone("+233200000000");
        r.setPassword("correct horse battery");
        return r;
    }

    @Test
    void anEmailUsedInSeveralTenantsDoesNotFailRegistration() {
        when(userRepository.existsByEmailIgnoreCase("ama@example.com")).thenReturn(true);

        assertThat(service.registerTenant(request()).getEmail()).isEqualTo("ama@example.com");
    }

    @Test
    void theAdminPhoneIsSaved() {
        service.registerTenant(request());

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(user.capture());
        assertThat(user.getValue().getPhone()).isEqualTo("+233200000000");
    }
}

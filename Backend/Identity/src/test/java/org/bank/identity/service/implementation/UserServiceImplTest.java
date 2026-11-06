package org.bank.identity.service.implementation;

import org.bank.common.dto.request.ChangePasswordRequest;
import org.bank.common.dto.request.CreateUserRequest;
import org.bank.common.dto.request.UpdateUserRequest;
import org.bank.common.dto.response.UserResponse;
import org.bank.common.exception.BusinessRuleException;
import org.bank.common.exception.DuplicateResourceException;
import org.bank.common.exception.ResourceNotFoundException;
import org.bank.common.security.access.BranchAccessEvaluator;
import org.bank.common.security.principal.AuthenticatedUser;
import org.bank.identity.mapper.UserMapper;
import org.bank.identity.model.Role;
import org.bank.identity.model.User;
import org.bank.identity.repository.RoleRepository;
import org.bank.identity.repository.UserRepository;
import org.bank.identity.security.RefreshTokenService;
import org.bank.identity.service.UserAccessPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String PASSWORD = "Str0ng!Passw0rd#1";

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock RefreshTokenService refreshTokenService;
    @Mock BranchAccessEvaluator branchAccess;

    private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private UserServiceImpl service;
    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, roleRepository, userMapper, encoder, refreshTokenService,
                new UserAccessPolicy(branchAccess), branchAccess);
        loginAs(Set.of("MANAGER"), "BR-01");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Set<String> roles, String branch) {
        AuthenticatedUser principal = new AuthenticatedUser(callerId, "caller", branch, roles, Set.of(), true, "1");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private User existingUser(String branch) {
        return User.builder().id(UUID.randomUUID()).username("teller1").email("teller1@bank.test")
                .passwordHash(encoder.encode(PASSWORD)).branchCode(branch).build();
    }

    @Test
    void createUserNormalisesHashesAndAssignsRoles() {
        Role teller = Role.builder().id(3L).name("TELLER").build();
        when(roleRepository.findAllById(Set.of(3L))).thenReturn(List.of(teller));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        UserResponse response = service.createUser(
                new CreateUserRequest("  Teller2 ", "Teller2@Bank.Test", PASSWORD, "BR-01", Set.of(3L)));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("teller2");
        assertThat(saved.getValue().getEmail()).isEqualTo("teller2@bank.test");
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, saved.getValue().getPasswordHash())).isTrue();
        assertThat(response.roles()).containsExactly("TELLER");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void createUserRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("teller1")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(
                new CreateUserRequest("teller1", "new@bank.test", PASSWORD, "BR-01", Set.of())))
                .isInstanceOf(DuplicateResourceException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUserWithAdminRoleRequiresAdminCaller() {
        Role admin = Role.builder().id(1L).name("ADMIN").build();
        when(roleRepository.findAllById(Set.of(1L))).thenReturn(List.of(admin));

        assertThatThrownBy(() -> service.createUser(
                new CreateUserRequest("boss", "boss@bank.test", PASSWORD, "BR-01", Set.of(1L))))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUserFailsWhenARoleDoesNotExist() {
        when(roleRepository.findAllById(Set.of(3L, 99L))).thenReturn(List.of(Role.builder().id(3L).name("TELLER").build()));

        assertThatThrownBy(() -> service.createUser(
                new CreateUserRequest("teller2", "t2@bank.test", PASSWORD, "BR-01", Set.of(3L, 99L))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void usersOfOtherBranchesAreReportedAsNotFound() {
        User other = existingUser("BR-99");
        when(userRepository.findWithRolesById(other.getId())).thenReturn(Optional.of(other));
        when(branchAccess.canAccess("BR-99")).thenReturn(false);

        assertThatThrownBy(() -> service.getUser(other.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void callerCanAlwaysReadOwnProfile() {
        User self = existingUser("BR-99");
        self.setId(callerId);
        when(userRepository.findWithRolesById(callerId)).thenReturn(Optional.of(self));

        assertThat(service.getUser(callerId).username()).isEqualTo("teller1");
    }

    @Test
    void assigningRoleRevokesSessionsOnlyWhenSomethingChanged() {
        User target = existingUser("BR-01");
        Role teller = Role.builder().id(3L).name("TELLER").build();
        when(userRepository.findWithRolesById(target.getId())).thenReturn(Optional.of(target));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(teller));
        when(branchAccess.canAccess("BR-01")).thenReturn(true);

        service.assignRoleToUser(target.getId(), 3L);
        service.assignRoleToUser(target.getId(), 3L);

        assertThat(target.getUserRoles()).hasSize(1);
        verify(refreshTokenService).revokeAllForUser(target.getId());
    }

    @Test
    void callerCannotChangeOwnRoles() {
        User self = existingUser("BR-01");
        self.setId(callerId);
        when(userRepository.findWithRolesById(callerId)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service.assignRoleToUser(callerId, 3L)).isInstanceOf(BusinessRuleException.class);
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void onlyAdminsMayGrantTheAdminRole() {
        User target = existingUser("BR-01");
        when(userRepository.findWithRolesById(target.getId())).thenReturn(Optional.of(target));
        when(roleRepository.findById(1L)).thenReturn(Optional.of(Role.builder().id(1L).name("ADMIN").build()));
        when(branchAccess.canAccess("BR-01")).thenReturn(true);

        assertThatThrownBy(() -> service.assignRoleToUser(target.getId(), 1L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void changePasswordRequiresCurrentPasswordAndRevokesSessions() {
        User self = existingUser("BR-01");
        when(userRepository.findByIdForUpdate(self.getId())).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service.changePassword(self.getId(), new ChangePasswordRequest("wrong", "An0ther!Passw0rd#2")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.changePassword(self.getId(), new ChangePasswordRequest(PASSWORD, PASSWORD)))
                .isInstanceOf(BusinessRuleException.class);
        verify(refreshTokenService, never()).revokeAllForUser(any());

        service.changePassword(self.getId(), new ChangePasswordRequest(PASSWORD, "An0ther!Passw0rd#2"));

        assertThat(encoder.matches("An0ther!Passw0rd#2", self.getPasswordHash())).isTrue();
        verify(refreshTokenService).revokeAllForUser(self.getId());
    }

    @Test
    void callerCannotDisableOwnMfa() {
        User self = existingUser("BR-01");
        self.setId(callerId);
        self.setMfaEnabled(true);
        when(userRepository.findWithRolesById(callerId)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service.updateUser(callerId, new UpdateUserRequest(null, null, false)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(self.getMfaEnabled()).isTrue();
    }

    @Test
    void updateAppliesOnlyProvidedFieldsAndRevokesSessionsOnMfaChange() {
        User target = existingUser("BR-01");
        when(userRepository.findWithRolesById(target.getId())).thenReturn(Optional.of(target));
        when(branchAccess.canAccess("BR-01")).thenReturn(true);

        UserResponse response = service.updateUser(target.getId(), new UpdateUserRequest(null, null, true));

        assertThat(response.mfaEnabled()).isTrue();
        assertThat(target.getEmail()).isEqualTo("teller1@bank.test");
        assertThat(target.getBranchCode()).isEqualTo("BR-01");
        verify(refreshTokenService).revokeAllForUser(target.getId());
    }
}

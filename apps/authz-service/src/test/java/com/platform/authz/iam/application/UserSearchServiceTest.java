package com.platform.authz.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.platform.authz.iam.domain.UserSearchAccessDeniedException;
import com.platform.authz.shared.security.ModuleScopeExtractor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class UserSearchServiceTest {

    @Mock
    private UserSearchPort userSearchPort;

    @Mock
    private ModuleScopeExtractor moduleScopeExtractor;

    @Mock
    private Authentication authentication;

    private UserSearchService service;

    @BeforeEach
    void setUp() {
        service = new UserSearchService(userSearchPort, moduleScopeExtractor);
    }

    @Test
    void search_WithPlatformAdmin_ShouldReturnAllUsersUnfiltered() {
        // Arrange
        when(moduleScopeExtractor.isPlatformAdmin(authentication)).thenReturn(true);
        when(moduleScopeExtractor.extractManageableModules(authentication)).thenReturn(List.of());
        when(userSearchPort.searchUsers("user", null)).thenReturn(List.of(
                new UserSummary("u1", "User One", "u1@demo", List.of("vendas")),
                new UserSummary("u2", "User Two", "u2@demo", List.of("estoque")),
                new UserSummary("u3", "User Three", "u3@demo", List.of("vendas", "estoque"))
        ));

        // Act
        List<UserSummary> result = service.search(authentication, "user", null);

        // Assert
        assertThat(result).hasSize(3);
        assertThat(result).extracting(UserSummary::userId).containsExactly("u1", "u2", "u3");
    }

    @Test
    void search_WithPlatformAdminAndModuleFilter_ShouldFilterByRequestedModule() {
        // Arrange
        when(moduleScopeExtractor.isPlatformAdmin(authentication)).thenReturn(true);
        when(moduleScopeExtractor.extractManageableModules(authentication)).thenReturn(List.of());
        when(userSearchPort.searchUsers("user", "vendas")).thenReturn(List.of(
                new UserSummary("u1", "User One", "u1@demo", List.of("vendas")),
                new UserSummary("u3", "User Three", "u3@demo", List.of("vendas", "estoque"))
        ));

        // Act
        List<UserSummary> result = service.search(authentication, "user", "vendas");

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).modules()).containsExactly("vendas");
        assertThat(result.get(1).modules()).containsExactly("vendas");
    }

    @Test
    void search_WithScopedManager_ShouldReturnOnlyUsersInManagedModules() {
        // Arrange
        when(moduleScopeExtractor.isPlatformAdmin(authentication)).thenReturn(false);
        when(moduleScopeExtractor.extractManageableModules(authentication)).thenReturn(List.of("vendas"));
        when(userSearchPort.searchUsers("user", null)).thenReturn(List.of(
                new UserSummary("u1", "Vendas Mgr", "u1@demo", List.of("vendas")),
                new UserSummary("u2", "Estoque Mgr", "u2@demo", List.of("estoque")),
                new UserSummary("u3", "Multi User", "u3@demo", List.of("vendas", "estoque"))
        ));

        // Act
        List<UserSummary> result = service.search(authentication, "user", null);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserSummary::userId).containsExactly("u1", "u3");
        assertThat(result.get(0).modules()).containsExactly("vendas");
        assertThat(result.get(1).modules()).containsExactly("vendas");
    }

    @Test
    void search_WithScopedManagerAndMultiModuleUser_ShouldFilterModulesToScope() {
        // Arrange
        when(moduleScopeExtractor.isPlatformAdmin(authentication)).thenReturn(false);
        when(moduleScopeExtractor.extractManageableModules(authentication)).thenReturn(List.of("vendas"));
        when(userSearchPort.searchUsers("user", null)).thenReturn(List.of(
                new UserSummary("u-multi", "Multi User", "multi@demo", List.of("vendas", "estoque", "financeiro"))
        ));

        // Act
        List<UserSummary> result = service.search(authentication, "user", null);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo("u-multi");
        assertThat(result.get(0).modules()).containsExactly("vendas");
    }

    @Test
    void search_WithScopedManagerRequestingOwnModule_ShouldReturnFilteredResults() {
        // Arrange
        when(moduleScopeExtractor.isPlatformAdmin(authentication)).thenReturn(false);
        when(moduleScopeExtractor.extractManageableModules(authentication)).thenReturn(List.of("vendas"));
        when(userSearchPort.searchUsers("user", "vendas")).thenReturn(List.of(
                new UserSummary("u1", "User One", "u1@demo", List.of("vendas"))
        ));

        // Act
        List<UserSummary> result = service.search(authentication, "user", "vendas");

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).modules()).containsExactly("vendas");
    }

    @Test
    void search_WithScopedManagerRequestingUnauthorizedModule_ShouldReturnEmpty() {
        // Arrange
        when(moduleScopeExtractor.isPlatformAdmin(authentication)).thenReturn(false);
        when(moduleScopeExtractor.extractManageableModules(authentication)).thenReturn(List.of("vendas"));

        // Act
        List<UserSummary> result = service.search(authentication, "user", "estoque");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void search_WithNoManagerRoleAndNotAdmin_ShouldThrowAccessDenied() {
        // Arrange
        when(moduleScopeExtractor.isPlatformAdmin(authentication)).thenReturn(false);
        when(moduleScopeExtractor.extractManageableModules(authentication)).thenReturn(List.of());

        // Act & Assert
        assertThatThrownBy(() -> service.search(authentication, "user", null))
                .isInstanceOf(UserSearchAccessDeniedException.class);
    }

    @Test
    void search_WithUserHavingNoModules_ShouldBeExcluded() {
        // Arrange
        when(moduleScopeExtractor.isPlatformAdmin(authentication)).thenReturn(false);
        when(moduleScopeExtractor.extractManageableModules(authentication)).thenReturn(List.of("vendas"));
        when(userSearchPort.searchUsers("user", null)).thenReturn(List.of(
                new UserSummary("u-no-mod", "No Modules", "nomod@demo", List.of())
        ));

        // Act
        List<UserSummary> result = service.search(authentication, "user", null);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void search_WithNullModulesInUser_ShouldBeExcluded() {
        // Arrange
        when(moduleScopeExtractor.isPlatformAdmin(authentication)).thenReturn(false);
        when(moduleScopeExtractor.extractManageableModules(authentication)).thenReturn(List.of("vendas"));
        when(userSearchPort.searchUsers("user", null)).thenReturn(List.of(
                new UserSummary("u-null-mod", "Null Modules", "null@demo", null)
        ));

        // Act
        List<UserSummary> result = service.search(authentication, "user", null);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void search_WithBlankRequestedModule_ShouldTreatAsNull() {
        // Arrange
        when(moduleScopeExtractor.isPlatformAdmin(authentication)).thenReturn(false);
        when(moduleScopeExtractor.extractManageableModules(authentication)).thenReturn(List.of("vendas"));
        when(userSearchPort.searchUsers("user", null)).thenReturn(List.of(
                new UserSummary("u1", "User One", "u1@demo", List.of("vendas"))
        ));

        // Act
        List<UserSummary> result = service.search(authentication, "user", "  ");

        // Assert
        assertThat(result).hasSize(1);
    }

    @Test
    void search_WithMultiModuleManager_ShouldReturnUsersFromAllManagedModules() {
        // Arrange
        when(moduleScopeExtractor.isPlatformAdmin(authentication)).thenReturn(false);
        when(moduleScopeExtractor.extractManageableModules(authentication)).thenReturn(List.of("vendas", "estoque"));
        when(userSearchPort.searchUsers("user", null)).thenReturn(List.of(
                new UserSummary("u1", "Vendas User", "u1@demo", List.of("vendas")),
                new UserSummary("u2", "Estoque User", "u2@demo", List.of("estoque")),
                new UserSummary("u3", "Financeiro User", "u3@demo", List.of("financeiro")),
                new UserSummary("u4", "Multi User", "u4@demo", List.of("vendas", "estoque", "financeiro"))
        ));

        // Act
        List<UserSummary> result = service.search(authentication, "user", null);

        // Assert
        assertThat(result).hasSize(3);
        assertThat(result).extracting(UserSummary::userId).containsExactly("u1", "u2", "u4");
        assertThat(result.get(2).modules()).containsExactlyInAnyOrder("vendas", "estoque");
    }
}

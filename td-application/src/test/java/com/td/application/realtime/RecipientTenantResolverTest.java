package com.td.application.realtime;

import com.td.application.sharedcore.OrganizationRepository;
import com.td.application.tenant.ResolveUserTenantUseCase;
import com.td.domain.sharedcore.Organization;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecipientTenantResolverTest {

    private final OrganizationRepository organizationRepository = Mockito.mock(OrganizationRepository.class);
    private final ResolveUserTenantUseCase resolveUserTenantUseCase = Mockito.mock(ResolveUserTenantUseCase.class);
    private final RecipientTenantResolver resolver = new RecipientTenantResolver(organizationRepository, resolveUserTenantUseCase);

    @Test
    void unitIdentifier_usesOrganizationAncestorTenant() {
        Organization child = Mockito.mock(Organization.class);
        Organization parent = Mockito.mock(Organization.class);
        UUID childId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        when(child.getId()).thenReturn(childId);
        when(child.getParentId()).thenReturn(parentId);
        when(parent.getId()).thenReturn(parentId);
        when(parent.getLinkedTenantId()).thenReturn("H05_01");
        when(organizationRepository.findByIdentifierAndDeletedOnIsNull("H05.01.05")).thenReturn(Optional.of(child));
        when(organizationRepository.findByIdAndDeletedOnIsNull(parentId)).thenReturn(Optional.of(parent));

        assertThat(resolver.resolve("H05.01.05")).contains("H05_01");
        verify(resolveUserTenantUseCase, never()).execute(anyString());
    }

    @Test
    void username_usesUserTenant_andIsCached() {
        when(resolveUserTenantUseCase.execute("vanthu.skhcn")).thenReturn(Optional.of("H05_01"));

        assertThat(resolver.resolve("vanthu.skhcn")).contains("H05_01");
        assertThat(resolver.resolve("VanThu.SKHCN")).contains("H05_01");
        assertThat(resolver.resolve("vanthu.skhcn")).contains("H05_01");
        verify(resolveUserTenantUseCase, times(1)).execute("vanthu.skhcn");
    }

    @Test
    void lookupFailure_isEmpty_notThrown() {
        when(resolveUserTenantUseCase.execute("loi")).thenThrow(new IllegalStateException("db down"));
        assertThat(resolver.resolve("loi")).isEmpty();
        assertThat(resolver.resolve(" ")).isEmpty();
    }
}

package com.example.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PeanutsService.
 * Repository layer is mocked to isolate service behavior.
 */
@ExtendWith(MockitoExtension.class)
class PeanutsServiceTest {

    @Mock
    private PeanutsRepository repository;

    @InjectMocks
    private PeanutsService service;

    @Nested
    @DisplayName("getPeanutsById")
    class GetPeanutsById {

        @Test
        @DisplayName("returns peanuts when found in repository")
        void returnsPeanutsWhenFound() {
            // Given
            Peanuts charlieBrown = createPeanuts(1L, "Charlie Brown", "The main character");
            when(repository.findById(1L)).thenReturn(Optional.of(charlieBrown));

            // When
            Peanuts result = service.getPeanutsById(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Charlie Brown");
            assertThat(result.getDescription()).isEqualTo("The main character");
            verify(repository).findById(1L);
        }

        @Test
        @DisplayName("returns null when not found in repository")
        void returnsNullWhenNotFound() {
            // Given
            when(repository.findById(999L)).thenReturn(Optional.empty());

            // When
            Peanuts result = service.getPeanutsById(999L);

            // Then
            assertThat(result).isNull();
            verify(repository).findById(999L);
        }

        @Test
        @DisplayName("queries repository with correct id")
        void queriesRepositoryWithCorrectId() {
            // Given
            when(repository.findById(42L)).thenReturn(Optional.empty());

            // When
            service.getPeanutsById(42L);

            // Then
            verify(repository).findById(42L);
            verify(repository, never()).findById(1L);
        }
    }

    @Nested
    @DisplayName("savePeanuts")
    class SavePeanuts {

        @Test
        @DisplayName("saves peanuts and returns saved entity")
        void savesPeanutsAndReturnsSaved() {
            // Given
            Peanuts input = createPeanuts(null, "Lucy", "Bossy and opinionated");
            Peanuts saved = createPeanuts(1L, "Lucy", "Bossy and opinionated");
            when(repository.save(any(Peanuts.class))).thenReturn(saved);

            // When
            Peanuts result = service.savePeanuts(input);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("Lucy");
            verify(repository).save(input);
        }

        @Test
        @DisplayName("passes entity to repository unchanged")
        void passesEntityToRepositoryUnchanged() {
            // Given
            Peanuts input = createPeanuts(null, "Linus", "Charlie Brown's best friend");
            when(repository.save(input)).thenReturn(input);

            // When
            service.savePeanuts(input);

            // Then
            verify(repository).save(input);
        }

        @Test
        @DisplayName("can update existing peanuts")
        void canUpdateExistingPeanuts() {
            // Given
            Peanuts existing = createPeanuts(5L, "Schroeder", "Plays piano");
            Peanuts updated = createPeanuts(5L, "Schroeder", "Beethoven enthusiast who plays piano");
            when(repository.save(existing)).thenReturn(updated);

            // When
            Peanuts result = service.savePeanuts(existing);

            // Then
            assertThat(result.getDescription()).isEqualTo("Beethoven enthusiast who plays piano");
            verify(repository).save(existing);
        }
    }

    /**
     * Helper method to create a Peanuts entity for testing.
     * Uses reflection to set the id field since there's no setter.
     */
    private Peanuts createPeanuts(Long id, String name, String description) {
        Peanuts peanuts = new Peanuts();
        peanuts.setName(name);
        peanuts.setDescription(description);
        
        if (id != null) {
            try {
                java.lang.reflect.Field idField = Peanuts.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(peanuts, id);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set id via reflection", e);
            }
        }
        
        return peanuts;
    }
}

package com.example.converter.controller.conversion;

import com.example.converter.dto.conversion.UnitConversionRequest;
import com.example.converter.dto.conversion.UnitConversionResponse;
import com.example.converter.entity.User;
import com.example.converter.repository.UserRepository;
import com.example.converter.service.unit.UnitConversionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Spring Boot REST Controller for unit conversion functionality.
 * Endpoint: /api/convert/unit (POST).
 * Input: JSON with value (double), fromUnit (string), toUnit (string).
 * Output: JSON containing convertedValue (double) and toUnit (string).
 * Uses UnitConversionService to handle business logic.
 * Includes Swagger annotations for this API.
 */
@RestController
@RequestMapping("/api/convert")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Unit Conversion", description = "APIs for unit conversion")
public class UnitConversionController {

    private final UnitConversionService unitConversionService;
    private final UserRepository userRepository;

    @PostMapping("/unit")
    @Operation(summary = "Convert unit", description = "Convert a value from one unit to another")
    public ResponseEntity<UnitConversionResponse> convertUnit(@Valid @RequestBody UnitConversionRequest request) {
        log.info("Received unit conversion request: {} {} to {}", 
                request.getValue(), request.getFromUnit(), request.getToUnit());

        try {
            // Get user ID from authentication context (null for anonymous)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Long userId = getUserIdFromAuthentication(authentication);

            UnitConversionResponse response = unitConversionService.convertUnit(request, userId);
            
            log.info("Unit conversion successful: {} {} = {} {}", 
                    request.getValue(), request.getFromUnit(), response.getConvertedValue(), response.getToUnit());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("Unit conversion failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error during unit conversion: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/unit/units")
    @Operation(summary = "Get available units", description = "Get all available units organized by category")
    public ResponseEntity<java.util.Map<String, String[]>> getAvailableUnits() {
        log.info("Fetching available units");
        try {
            java.util.Map<String, String[]> availableUnits = unitConversionService.getAvailableUnits();
            return ResponseEntity.ok(availableUnits);
        } catch (Exception e) {
            log.error("Error fetching available units: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Extract user ID from authentication context
     * @param authentication the authentication object
     * @return user ID
     */
    private Long getUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("No authenticated user found");
            return null;
        }
        
        try {
            String username = authentication.getName();
            log.debug("Getting user ID for authenticated username: {}", username);
            
            Optional<User> userOptional = userRepository.findByUsername(username);
            if (userOptional.isPresent()) {
                Long userId = userOptional.get().getId();
                log.debug("Found user ID {} for username: {}", userId, username);
                return userId;
            } else {
                log.warn("No user found for username: {}", username);
                return null;
            }
        } catch (Exception e) {
            log.error("Error getting user ID from authentication: {}", e.getMessage(), e);
            return null;
        }
    }
} 
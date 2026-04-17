package com.platform.authz.iam.api;

import com.platform.authz.iam.api.dto.UserSummaryDto;
import com.platform.authz.iam.application.UserSearchService;
import com.platform.authz.iam.application.UserSummary;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/users")
public class UserSearchController {

    private final UserSearchService userSearchService;

    public UserSearchController(
            UserSearchService userSearchService
    ) {
        this.userSearchService = Objects.requireNonNull(userSearchService, "userSearchService must not be null");
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserSummaryDto>> searchUsers(
            @RequestParam("q") @NotBlank String query,
            @RequestParam(value = "moduleId", required = false) String moduleId,
            Authentication authentication
    ) {
        List<UserSummary> results = userSearchService.search(authentication, query, moduleId);
        List<UserSummaryDto> response = results.stream()
                .map(UserSearchController::toDto)
                .toList();
        return ResponseEntity.ok(response);
    }

    private static UserSummaryDto toDto(UserSummary summary) {
        return new UserSummaryDto(
                summary.userId(),
                summary.displayName(),
                summary.email(),
                summary.modules()
        );
    }
}

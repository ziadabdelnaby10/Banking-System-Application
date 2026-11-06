package org.bank.common.security.testsupport;

import org.bank.common.security.principal.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class ProbeController {

    @GetMapping("/probe/open")
    Map<String, String> open() {
        return Map.of("status", "open");
    }

    @GetMapping("/probe/me")
    Map<String, Object> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("username", user.username(), "branch", user.branchCode(), "mfa", user.mfaVerified());
    }

    @GetMapping("/probe/permission")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    Map<String, String> permission() {
        return Map.of("status", "ok");
    }

    @GetMapping("/probe/admin")
    @PreAuthorize("hasRole('ADMIN')")
    Map<String, String> admin() {
        return Map.of("status", "ok");
    }

    @GetMapping("/probe/branch/{branch}")
    @PreAuthorize("@branchAccess.canAccess(#branch)")
    Map<String, String> branch(@PathVariable String branch) {
        return Map.of("branch", branch);
    }
}

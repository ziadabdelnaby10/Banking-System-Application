package org.bank.common.security.reactivetestsupport;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * TEST HELPER: the smallest possible WebFlux application. It exists only so the tests can start a real
 * reactive app that loads the CommonSecurity auto-configuration, like the Gateway does.
 * Minimal reactive application used to exercise the reactive auto-configuration like a real gateway would.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackageClasses = ReactiveProbeController.class)
public class ReactiveProbeApplication {
}

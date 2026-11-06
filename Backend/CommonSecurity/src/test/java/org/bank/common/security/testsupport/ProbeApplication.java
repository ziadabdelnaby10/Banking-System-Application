package org.bank.common.security.testsupport;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.ComponentScan;

/** Minimal application used to exercise the auto-configuration the way a real service would. */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackageClasses = ProbeController.class)
public class ProbeApplication {
}

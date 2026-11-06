package org.bank.common.dto.response;

import java.io.Serializable;

public record RoleResponse(Long id, String name, String description) implements Serializable {
}

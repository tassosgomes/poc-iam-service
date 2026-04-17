package com.platform.authz.modules.application;

import java.util.UUID;

public record RotateKeyCommand(
        UUID moduleId,
        String rotatedBy,
        String sourceIp
) {
}

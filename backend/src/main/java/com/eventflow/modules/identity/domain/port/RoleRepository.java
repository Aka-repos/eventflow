package com.eventflow.modules.identity.domain.port;

import com.eventflow.modules.identity.domain.Role;
import com.eventflow.modules.identity.domain.RoleCode;

public interface RoleRepository {

    Role getByCode(RoleCode code);
}

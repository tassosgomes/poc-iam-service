package com.platform.authz.iam.application;

import java.util.List;

public interface UserSearchPort {

    List<UserSummary> searchUsers(String query, String moduleFilter);

    boolean userExists(String userId);
}

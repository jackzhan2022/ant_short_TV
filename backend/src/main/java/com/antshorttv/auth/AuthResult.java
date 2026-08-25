package com.antshorttv.auth;

import com.antshorttv.authsession.IssuedSession;

public record AuthResult(AuthSessionResponse response, IssuedSession issuedSession) {
}

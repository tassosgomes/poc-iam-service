# ========================================
# CyberArk Mock - OAuth2 Server for Local Development
# ========================================
# Based on NaviKt's Mock OAuth2 Server
# https://github.com/navikt/mock-oauth2-server
#
# This mock server simulates CyberArk's OIDC provider with:
# - Standard OIDC endpoints (.well-known, authorize, token, jwks)
# - Custom claims via debugger endpoint
# - Interactive login UI for testing
#
# Usage:
# - OIDC Discovery: http://localhost:8080/default/.well-known/openid-configuration
# - Debugger: http://localhost:8080/default/debugger
# - To generate a token with custom claims, use the debugger endpoint:
#   curl -X POST http://localhost:8080/default/token?subject=user-admin&claims={"module_membership":["platform"],"roles":["PLATFORM_ADMIN"]}
# ========================================

FROM ghcr.io/navikt/mock-oauth2-server:2.1.1

# Embed the JSON configuration used by the mock server at runtime.
COPY users.json /config.json

# Expose the default port
EXPOSE 8080

# Environment variables (can be overridden in docker-compose)
ENV SERVER_PORT=8080
ENV JSON_CONFIG_PATH=/config.json

# Default command (inherited from base image)
# The mock-oauth2-server will automatically create an issuer "default" and provide
# standard OIDC endpoints along with a debugger UI for generating test tokens

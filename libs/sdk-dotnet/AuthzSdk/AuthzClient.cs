using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using AuthzSdk.Exceptions;
using AuthzSdk.Models;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Options;
using Microsoft.Net.Http.Headers;

namespace AuthzSdk;

public sealed class AuthzClient : IAuthzClient
{
    private static readonly JsonSerializerOptions JsonSerializerOptions = new(JsonSerializerDefaults.Web);

    private readonly HttpClient _httpClient;
    private readonly IHttpContextAccessor _httpContextAccessor;
    private readonly AuthzOptions _options;

    public AuthzClient(
        HttpClient httpClient,
        IOptions<AuthzOptions> options,
        IHttpContextAccessor httpContextAccessor)
    {
        _httpClient = httpClient ?? throw new ArgumentNullException(nameof(httpClient));
        _httpContextAccessor = httpContextAccessor ?? throw new ArgumentNullException(nameof(httpContextAccessor));
        _options = options?.Value ?? throw new ArgumentNullException(nameof(options));
    }

    public async Task<UserPermissions> GetUserPermissionsAsync(string userId, CancellationToken cancellationToken = default)
    {
        ValidateText(userId, nameof(userId));

        using var request = new HttpRequestMessage(HttpMethod.Get, $"/v1/users/{Uri.EscapeDataString(userId)}/permissions");
        ApplyRuntimeAuthorization(request.Headers);

        return await SendAsync<UserPermissions>(
            request,
            $"Failed to fetch permissions for userId='{userId}'.",
            cancellationToken).ConfigureAwait(false);
    }

    public async Task<bool> CheckPermissionAsync(string userId, string permission, CancellationToken cancellationToken = default)
    {
        ValidateText(userId, nameof(userId));
        ValidateText(permission, nameof(permission));

        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/authz/check");
        request.Content = JsonContent.Create(new CheckPermissionRequest(userId, permission));
        ApplyRuntimeAuthorization(request.Headers);

        var response = await SendAsync<CheckPermissionResponse>(
            request,
            $"Failed to check permission '{permission}' for userId='{userId}'.",
            cancellationToken).ConfigureAwait(false);

        return response.Allowed;
    }

    public async Task<SyncResult> SyncAsync(SyncRequest request, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(request);

        using var httpRequest = new HttpRequestMessage(HttpMethod.Post, "/v1/catalog/sync");
        httpRequest.Content = JsonContent.Create(request);
        ApplyModuleAuthorization(httpRequest.Headers);

        return await SendAsync<SyncResult>(
            httpRequest,
            $"Failed to sync permissions for moduleId='{request.ModuleId}'.",
            cancellationToken).ConfigureAwait(false);
    }

    private async Task<TResponse> SendAsync<TResponse>(
        HttpRequestMessage request,
        string errorMessage,
        CancellationToken cancellationToken)
    {
        try
        {
            using var response = await _httpClient.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, cancellationToken)
                .ConfigureAwait(false);

            if (!response.IsSuccessStatusCode)
            {
                throw await CreateExceptionAsync(response, errorMessage, cancellationToken).ConfigureAwait(false);
            }

            var payload = await response.Content.ReadFromJsonAsync<TResponse>(JsonSerializerOptions, cancellationToken)
                .ConfigureAwait(false);

            return payload ?? throw new AuthzClientException($"{errorMessage} The response body was empty.");
        }
        catch (AuthzClientException)
        {
            throw;
        }
        catch (OperationCanceledException exception) when (!cancellationToken.IsCancellationRequested)
        {
            throw new AuthzClientException($"{errorMessage} The request timed out.", exception);
        }
        catch (HttpRequestException exception)
        {
            throw new AuthzClientException($"{errorMessage} The HTTP request failed.", exception);
        }
        catch (Exception exception)
        {
            throw new AuthzClientException(errorMessage, exception);
        }
    }

    private void ApplyRuntimeAuthorization(HttpRequestHeaders headers)
    {
        var token = _httpContextAccessor.HttpContext?.Request.Headers.Authorization.ToString();
        if (string.IsNullOrWhiteSpace(token))
        {
            throw new AuthzClientException("No runtime Authorization header was found in the current request.");
        }

        headers.TryAddWithoutValidation(HeaderNames.Authorization, token);
    }

    private void ApplyModuleAuthorization(HttpRequestHeaders headers)
    {
        if (string.IsNullOrWhiteSpace(_options.ModuleKey))
        {
            throw new AuthzClientException("Authz module key is required for catalog sync.");
        }

        headers.Authorization = new AuthenticationHeaderValue("Bearer", _options.ModuleKey);

        if (!string.IsNullOrWhiteSpace(_options.ModuleId))
        {
            headers.TryAddWithoutValidation("X-Module-Id", _options.ModuleId);
        }
    }

    private static async Task<AuthzClientException> CreateExceptionAsync(
        HttpResponseMessage response,
        string errorMessage,
        CancellationToken cancellationToken)
    {
        var detail = response.Content is null
            ? string.Empty
            : await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false);

        var message = string.IsNullOrWhiteSpace(detail)
            ? $"{errorMessage} StatusCode={(int)response.StatusCode}."
            : $"{errorMessage} StatusCode={(int)response.StatusCode}. Response='{detail}'.";

        return new AuthzClientException(message, (int)response.StatusCode);
    }

    private static void ValidateText(string value, string paramName)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            throw new ArgumentException("Value cannot be null or whitespace.", paramName);
        }
    }

    private sealed record CheckPermissionRequest(string UserId, string Permission);

    private sealed record CheckPermissionResponse(bool Allowed);
}

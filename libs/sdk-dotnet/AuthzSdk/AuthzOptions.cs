namespace AuthzSdk;

public sealed class AuthzOptions
{
    public const string SectionName = "Authz";

    public string BaseUrl { get; set; } = string.Empty;

    public string ModuleId { get; set; } = string.Empty;

    public string ModuleKey { get; set; } = string.Empty;

    public TimeSpan Timeout { get; set; } = TimeSpan.FromSeconds(2);
}

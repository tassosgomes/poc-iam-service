namespace AuthzSdk.Exceptions;

public sealed class AuthzClientException : Exception
{
    public AuthzClientException(string message)
        : this(message, statusCode: 0, innerException: null)
    {
    }

    public AuthzClientException(string message, Exception innerException)
        : this(message, statusCode: 0, innerException)
    {
    }

    public AuthzClientException(string message, int statusCode)
        : this(message, statusCode, innerException: null)
    {
    }

    public AuthzClientException(string message, int statusCode, Exception? innerException)
        : base(message, innerException)
    {
        StatusCode = statusCode;
    }

    public int StatusCode { get; }

    public bool IsClientError => StatusCode is >= 400 and < 500;

    public bool IsServerError => StatusCode >= 500;
}

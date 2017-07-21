package voiceops.kubernetescontrol.auth;

public class ProviderSslContextFactory
{
  public static ProviderSslContext getSslContext(String criteria, String region)
  {
    if ( criteria.equals("aws") )
      return new awsSslContext();
    else if ( criteria.equals("azure") )
      return null;

    return null;
  }
}

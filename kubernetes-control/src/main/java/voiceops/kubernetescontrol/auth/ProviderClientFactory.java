package voiceops.kubernetescontrol.auth;

public class ProviderClientFactory
{
  public static ProviderClient getClient(String criteria, String region)
  {
    if ( criteria.equals("aws") )
      return new AwsClient();
    else if ( criteria.equals("azure") )
      return null;

    return null;
  }
}
